package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.LedgerAccountType;
import com.example.distrobackend.Domain.enums.LedgerEntryDirection;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.LedgerJournalResponse;
import com.example.distrobackend.repository.LedgerAccountRepository;
import com.example.distrobackend.repository.LedgerJournalRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinancialLedgerService {
    private static final String CAPTURE_EVENT = "PAYMENT_CAPTURED";

    private final LedgerAccountRepository accountRepository;
    private final LedgerJournalRepository journalRepository;
    private final CommissionRuleService commissionRuleService;

    @Transactional
    public FinancialBreakdown recordCapturedPayment(String sourceType, UUID sourceId,
                                                   Organization beneficiary, BigDecimal gross,
                                                   BigDecimal providerFee, String currency,
                                                   boolean simulated) {
        if (sourceType == null || sourceType.isBlank() || sourceId == null || beneficiary == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A payment ledger source and beneficiary are required");
        }
        if (gross == null || gross.signum() <= 0 || providerFee == null || providerFee.signum() < 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Payment amounts must be valid positive values");
        }
        String normalizedCurrency = normalizeCurrency(currency);
        int currencyScale;
        try {
            currencyScale = Currency.getInstance(normalizedCurrency).getDefaultFractionDigits();
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "currency must be a supported ISO 4217 currency code");
        }
        if (currencyScale < 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "currency must have a defined minor-unit precision");
        }
        BigDecimal normalizedGross = requireCurrencyScale(gross, "gross", currencyScale);
        BigDecimal normalizedProviderFee = requireCurrencyScale(providerFee, "providerFee", currencyScale);
        if (!simulated) {
            LedgerJournal existing = journalRepository
                    .findBySourceTypeAndSourceIdAndEventType(sourceType, sourceId, CAPTURE_EVENT).orElse(null);
            if (existing != null) return breakdownFromJournal(existing);
        }
        CommissionRuleService.CommissionDecision decision = commissionRuleService.resolve(
                beneficiary.getType(), normalizedCurrency, OffsetDateTime.now());
        FinancialBreakdown breakdown = decision.calculate(normalizedGross, normalizedProviderFee);
        if (simulated) return breakdown;

        LedgerAccount clearing = platformAccount(LedgerAccountType.PROVIDER_CLEARING, normalizedCurrency);
        LedgerAccount payable = organizationAccount(LedgerAccountType.ORGANIZATION_PAYABLE,
                normalizedCurrency, beneficiary);
        LedgerAccount revenue = platformAccount(LedgerAccountType.PLATFORM_REVENUE, normalizedCurrency);
        LedgerAccount processingPayable = platformAccount(LedgerAccountType.PAYMENT_PROCESSING_PAYABLE,
                normalizedCurrency);

        LedgerJournal journal = new LedgerJournal();
        journal.setSourceType(sourceType);
        journal.setSourceId(sourceId);
        journal.setEventType(CAPTURE_EVENT);
        journal.setCommissionRuleId(breakdown.commissionRuleId());
        journal.setCurrency(normalizedCurrency);
        journal.setDescription("Confirmed payment allocation for " + beneficiary.getName());
        journal.setSimulated(false);
        addEntry(journal, clearing, LedgerEntryDirection.DEBIT, breakdown.gross(), normalizedCurrency,
                "Payment received by provider");
        addEntry(journal, payable, LedgerEntryDirection.CREDIT, breakdown.supplierAmount(), normalizedCurrency,
                "Amount payable to organization");
        addEntry(journal, revenue, LedgerEntryDirection.CREDIT, breakdown.commission(), normalizedCurrency,
                "LogiFlow commission");
        addEntry(journal, processingPayable, LedgerEntryDirection.CREDIT, breakdown.providerFee(), normalizedCurrency,
                "Provider fee payable");
        assertBalanced(journal);
        journalRepository.save(journal);
        return breakdown;
    }

    @Transactional(readOnly = true)
    public Page<LedgerJournalResponse> list(AuthenticatedUser actor, Pageable pageable) {
        requirePlatformAdmin(actor);
        return journalRepository.findAllDetailed(pageable).map(LedgerJournalResponse::from);
    }

    @Transactional(readOnly = true)
    public LedgerJournalResponse get(AuthenticatedUser actor, UUID journalId) {
        requirePlatformAdmin(actor);
        return journalRepository.findDetailedById(journalId)
                .map(LedgerJournalResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Ledger journal not found"));
    }

    private LedgerAccount platformAccount(LedgerAccountType type, String currency) {
        accountRepository.insertPlatformAccountIfMissing(type.name(), currency);
        return accountRepository.findPlatformAccount(type, currency)
                .orElseThrow(() -> new IllegalStateException("Platform ledger account could not be created"));
    }

    private LedgerAccount organizationAccount(LedgerAccountType type, String currency, Organization organization) {
        accountRepository.insertOrganizationAccountIfMissing(type.name(), organization.getId(), currency);
        return accountRepository.findOrganizationAccount(type, currency, organization.getId())
                .orElseThrow(() -> new IllegalStateException("Organization ledger account could not be created"));
    }

    private static FinancialBreakdown breakdownFromJournal(LedgerJournal journal) {
        BigDecimal gross = amountFor(journal, LedgerAccountType.PROVIDER_CLEARING, LedgerEntryDirection.DEBIT);
        if (gross == null || gross.signum() <= 0) {
            throw new ApiException(ErrorCode.CONFLICT, "Existing capture journal is incomplete");
        }
        BigDecimal supplier = amountOrZero(journal, LedgerAccountType.ORGANIZATION_PAYABLE,
                LedgerEntryDirection.CREDIT);
        BigDecimal commission = amountOrZero(journal, LedgerAccountType.PLATFORM_REVENUE,
                LedgerEntryDirection.CREDIT);
        BigDecimal providerFee = amountOrZero(journal, LedgerAccountType.PAYMENT_PROCESSING_PAYABLE,
                LedgerEntryDirection.CREDIT);
        if (gross.compareTo(supplier.add(commission).add(providerFee)) != 0) {
            throw new ApiException(ErrorCode.CONFLICT, "Existing capture journal is unbalanced");
        }
        return new FinancialBreakdown(gross, providerFee, commission, supplier,
                journal.getCurrency(), journal.getCommissionRuleId());
    }

    private static BigDecimal amountOrZero(LedgerJournal journal, LedgerAccountType type,
                                           LedgerEntryDirection direction) {
        BigDecimal amount = amountFor(journal, type, direction);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static BigDecimal amountFor(LedgerJournal journal, LedgerAccountType type,
                                        LedgerEntryDirection direction) {
        var entries = journal.getEntries().stream()
                .filter(entry -> entry.getAccount().getAccountType() == type && entry.getDirection() == direction)
                .map(LedgerEntry::getAmount).toList();
        return entries.isEmpty() ? null : entries.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal requireCurrencyScale(BigDecimal amount, String field, int currencyScale) {
        try {
            return amount.setScale(currencyScale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    field + " exceeds the minor-unit precision for the selected currency");
        }
    }

    private static void addEntry(LedgerJournal journal, LedgerAccount account, LedgerEntryDirection direction,
                                 BigDecimal amount, String currency, String description) {
        if (amount.signum() == 0) return;
        LedgerEntry entry = new LedgerEntry();
        entry.setAccount(account);
        entry.setDirection(direction);
        entry.setAmount(amount);
        entry.setCurrency(currency);
        entry.setDescription(description);
        journal.addEntry(entry);
    }

    private static void assertBalanced(LedgerJournal journal) {
        BigDecimal debit = journal.getEntries().stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.DEBIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = journal.getEntries().stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.CREDIT)
                .map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debit.compareTo(credit) != 0) {
            throw new IllegalStateException("Unbalanced financial journal");
        }
    }

    private static String normalizeCurrency(String currency) {
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "currency must be a three-letter ISO currency code");
        }
        return normalized;
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    public record FinancialBreakdown(BigDecimal gross, BigDecimal providerFee, BigDecimal commission,
                                     BigDecimal supplierAmount, String currency, UUID commissionRuleId) {}
}
