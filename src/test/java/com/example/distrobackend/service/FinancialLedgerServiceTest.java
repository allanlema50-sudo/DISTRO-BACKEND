package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.LedgerAccount;
import com.example.distrobackend.Domain.entity.LedgerEntry;
import com.example.distrobackend.Domain.entity.LedgerJournal;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.LedgerAccountType;
import com.example.distrobackend.Domain.enums.LedgerEntryDirection;
import com.example.distrobackend.repository.LedgerAccountRepository;
import com.example.distrobackend.repository.LedgerEntryRepository;
import com.example.distrobackend.repository.LedgerJournalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialLedgerServiceTest {

    @Mock private LedgerAccountRepository accountRepository;
    @Mock private LedgerJournalRepository journalRepository;
    @Mock private LedgerEntryRepository entryRepository;
    @Mock private CommissionRuleService commissionRuleService;

    private FinancialLedgerService ledger;
    private Organization distributor;
    private LedgerAccount clearing;
    private LedgerAccount payable;

    @BeforeEach
    void setUp() {
        ledger = new FinancialLedgerService(accountRepository, journalRepository, entryRepository, commissionRuleService);
        distributor = new Organization();
        distributor.setId(UUID.randomUUID());
        distributor.setName("Builder Depot");
        clearing = account(LedgerAccountType.PROVIDER_CLEARING, null);
        payable = account(LedgerAccountType.ORGANIZATION_PAYABLE, distributor);
    }

    @Test
    void customerCaptureCreditsTheDistributorTheFullAmount() {
        UUID paymentId = UUID.randomUUID();
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_CAPTURED")).thenReturn(Optional.empty());
        when(accountRepository.findPlatformAccount(LedgerAccountType.PROVIDER_CLEARING, "KES"))
                .thenReturn(Optional.of(clearing));
        when(accountRepository.findOrganizationAccount(
                LedgerAccountType.ORGANIZATION_PAYABLE, "KES", distributor.getId()))
                .thenReturn(Optional.of(payable));
        when(journalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ledger.recordCustomerOrderCapture(paymentId, distributor, new BigDecimal("1250.00"), "KES");

        ArgumentCaptor<LedgerJournal> saved = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(journalRepository).save(saved.capture());
        assertThat(saved.getValue().getEntries()).hasSize(2);
        assertThat(amount(saved.getValue(), clearing, LedgerEntryDirection.DEBIT)).isEqualByComparingTo("1250.00");
        assertThat(amount(saved.getValue(), payable, LedgerEntryDirection.CREDIT)).isEqualByComparingTo("1250.00");
        verifyNoInteractions(commissionRuleService);
    }

    @Test
    void customerCaptureDoesNotPostTwice() {
        UUID paymentId = UUID.randomUUID();
        LedgerJournal existing = new LedgerJournal();
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_CAPTURED")).thenReturn(Optional.of(existing));

        ledger.recordCustomerOrderCapture(paymentId, distributor, new BigDecimal("10.00"), "KES");

        verify(journalRepository, never()).save(any());
        verifyNoInteractions(commissionRuleService);
    }

    @Test
    void reversalFlipsTheCapturedJournal() {
        UUID paymentId = UUID.randomUUID();
        LedgerJournal capture = new LedgerJournal();
        capture.setCurrency("KES");
        capture.addEntry(entry(clearing, LedgerEntryDirection.DEBIT, "1250.00"));
        capture.addEntry(entry(payable, LedgerEntryDirection.CREDIT, "1187.50"));
        LedgerAccount revenue = account(LedgerAccountType.PLATFORM_REVENUE, null);
        capture.addEntry(entry(revenue, LedgerEntryDirection.CREDIT, "62.50"));
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_REVERSED")).thenReturn(Optional.empty());
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_REFUNDED")).thenReturn(Optional.empty());
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_CAPTURED")).thenReturn(Optional.of(capture));
        when(journalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ledger.reverseCapturedPayment("CUSTOMER_ORDER_PAYMENT", paymentId);

        ArgumentCaptor<LedgerJournal> saved = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(journalRepository).save(saved.capture());
        assertThat(saved.getValue().getEventType()).isEqualTo("PAYMENT_REVERSED");
        assertThat(amount(saved.getValue(), clearing, LedgerEntryDirection.CREDIT)).isEqualByComparingTo("1250.00");
        assertThat(amount(saved.getValue(), payable, LedgerEntryDirection.DEBIT)).isEqualByComparingTo("1187.50");
        assertThat(amount(saved.getValue(), revenue, LedgerEntryDirection.DEBIT)).isEqualByComparingTo("62.50");
        verifyNoInteractions(entryRepository);
    }

    @Test
    void payableBalanceIsReportedNegativeWhenReversalsExceedWhatRemains() {
        when(entryRepository.organizationPayableBalance(distributor.getId(), "KES"))
                .thenReturn(new BigDecimal("-300"));

        assertThat(ledger.payableBalance(distributor.getId(), "KES")).isEqualByComparingTo("-300.00");
    }

    @Test
    void payoutJournalDebitsThePayable() {
        LedgerAccount payoutClearing = account(LedgerAccountType.PAYOUT_CLEARING, null);
        UUID payoutId = UUID.randomUUID();
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "ORGANIZATION_PAYOUT", payoutId, "PAYOUT")).thenReturn(Optional.empty());
        when(accountRepository.findOrganizationAccount(
                LedgerAccountType.ORGANIZATION_PAYABLE, "KES", distributor.getId()))
                .thenReturn(Optional.of(payable));
        when(accountRepository.findPlatformAccount(eq(LedgerAccountType.PAYOUT_CLEARING), eq("KES")))
                .thenReturn(Optional.of(payoutClearing));
        when(journalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ledger.recordOrganizationPayout(payoutId, distributor, new BigDecimal("40.00"), "KES", "FT26001");

        ArgumentCaptor<LedgerJournal> saved = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(journalRepository).save(saved.capture());
        assertThat(amount(saved.getValue(), payable, LedgerEntryDirection.DEBIT)).isEqualByComparingTo("40.00");
        assertThat(amount(saved.getValue(), payoutClearing, LedgerEntryDirection.CREDIT)).isEqualByComparingTo("40.00");
        verifyNoInteractions(commissionRuleService);
    }

    private static LedgerAccount account(LedgerAccountType type, Organization organization) {
        LedgerAccount account = new LedgerAccount();
        account.setAccountType(type);
        account.setOrganization(organization);
        account.setCurrency("KES");
        return account;
    }

    private static LedgerEntry entry(LedgerAccount account, LedgerEntryDirection direction, String amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccount(account);
        entry.setDirection(direction);
        entry.setAmount(new BigDecimal(amount));
        entry.setCurrency("KES");
        entry.setDescription(direction.name());
        return entry;
    }

    private static BigDecimal amount(LedgerJournal journal, LedgerAccount account, LedgerEntryDirection direction) {
        return journal.getEntries().stream()
                .filter(entry -> entry.getAccount() == account && entry.getDirection() == direction)
                .map(LedgerEntry::getAmount)
                .findFirst()
                .orElseThrow();
    }
}
