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
import java.time.OffsetDateTime;
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
        CommissionRuleService.CommissionDecision decision = commissionRuleService.resolve(
                beneficiary.getType(), normalizedCurrency, OffsetDateTime.now());
        FinancialBreakdown breakdown = decision.calculate(gross, providerFee);
        if (simulated) return breakdown;

        if (journalRepository.findBySourceTypeAndSourceIdAndEventType(sourceType, sourceId, CAPTURE_EVENT).isPresent()) {
            return breakdown;
        }

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
        return accountRepository.findPlatformAccount(type, currency).orElseGet(() -> {
            LedgerAccount account = new LedgerAccount();
            account.setAccountType(type);
            account.setCurrency(currency);
            account.setActive(true);
            return accountRepository.save(account);
        });
    }

    private LedgerAccount organizationAccount(LedgerAccountType type, String currency, Organization organization) {
        return accountRepository.findOrganizationAccount(type, currency, organization.getId()).orElseGet(() -> {
            LedgerAccount account = new LedgerAccount();
            account.setAccountType(type);
            account.setCurrency(currency);
            account.setOrganization(organization);
            account.setActive(true);
            return accountRepository.save(account);
        });
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
