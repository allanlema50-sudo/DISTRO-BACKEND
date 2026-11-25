package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.LedgerAccount;
import com.example.distrobackend.Domain.entity.LedgerEntry;
import com.example.distrobackend.Domain.entity.LedgerJournal;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.LedgerAccountType;
import com.example.distrobackend.Domain.enums.LedgerEntryDirection;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.repository.LedgerAccountRepository;
import com.example.distrobackend.repository.LedgerEntryRepository;
import com.example.distrobackend.repository.LedgerJournalRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialLedgerServiceTest {
    @Mock private LedgerAccountRepository accountRepository;
    @Mock private LedgerJournalRepository journalRepository;
    @Mock private LedgerEntryRepository entryRepository;
    @Mock private CommissionRuleService commissionRuleService;

    @Test
    void recordsABalancedCaptureJournalAndCommission() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, entryRepository, commissionRuleService);
        Organization distributor = new Organization();
        distributor.setId(UUID.randomUUID());
        distributor.setName("Distributor");
        distributor.setType(Organizationtype.DISTRIBUTOR);
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(accountRepository.findPlatformAccount(any(), eq("KES")))
                .thenAnswer(invocation -> Optional.of(account(invocation.getArgument(0))));
        when(accountRepository.findOrganizationAccount(any(), eq("KES"), eq(distributor.getId())))
                .thenAnswer(invocation -> Optional.of(account(LedgerAccountType.ORGANIZATION_PAYABLE)));
        when(commissionRuleService.resolve(eq(Organizationtype.DISTRIBUTOR), eq("KES"), any()))
                .thenReturn(new CommissionRuleService.CommissionDecision(
                        UUID.randomUUID(), new BigDecimal("5.00"), BigDecimal.ZERO, "KES"));

        FinancialLedgerService.FinancialBreakdown result = service.recordCapturedPayment(
                "CUSTOMER_ORDER_PAYMENT", UUID.randomUUID(), distributor,
                new BigDecimal("100.00"), BigDecimal.ZERO, "KES", false);

        assertThat(result.commission()).isEqualByComparingTo("5.00");
        assertThat(result.supplierAmount()).isEqualByComparingTo("95.00");
        ArgumentCaptor<LedgerJournal> captor = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(journalRepository).save(captor.capture());
        LedgerJournal journal = captor.getValue();
        BigDecimal debits = journal.getEntries().stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.DEBIT)
                .map(entry -> entry.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = journal.getEntries().stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.CREDIT)
                .map(entry -> entry.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo(credits);
        assertThat(journal.getEntries()).hasSize(3);
    }

    @Test
    void doesNotCreateASecondJournalForTheSamePaymentEvent() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, entryRepository, commissionRuleService);
        Organization distributor = new Organization();
        distributor.setId(UUID.randomUUID());
        distributor.setType(Organizationtype.DISTRIBUTOR);
        UUID sourceId = UUID.randomUUID();
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType("PAYMENT", sourceId, "PAYMENT_CAPTURED"))
                .thenReturn(Optional.of(captureJournal()));

        FinancialLedgerService.FinancialBreakdown result = service.recordCapturedPayment("PAYMENT", sourceId, distributor,
                new BigDecimal("100.00"), BigDecimal.ZERO, "KES", false);

        assertThat(result.gross()).isEqualByComparingTo("100.00");
        assertThat(result.supplierAmount()).isEqualByComparingTo("95.00");
        assertThat(result.commission()).isEqualByComparingTo("5.00");
        verify(journalRepository, never()).save(any(LedgerJournal.class));
        verifyNoInteractions(commissionRuleService);
    }

    @Test
    void aDelayedSuccessForAReversedTransferDoesNotCreateAnotherPayout() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, entryRepository, commissionRuleService);
        SupplierSettlement settlement = settlement("transfer-1");
        LedgerJournal payout = payoutJournal("PAYOUT_SETTLED_1", "transfer-1", "Confirmed supplier payout transfer-1");
        LedgerJournal reversal = payoutJournal("PAYOUT_REVERSED_1", "transfer-1",
                "Reversal of confirmed supplier payout transfer-1");
        when(journalRepository.findBySourceTypeAndSourceIdOrderByCreatedAtAsc(
                "SUPPLIER_SETTLEMENT", settlement.getId())).thenReturn(java.util.List.of(payout, reversal));

        service.recordSupplierPayout(settlement);

        verify(journalRepository, never()).save(any(LedgerJournal.class));
    }

    @Test
    void recordsANewPayoutReferenceAfterThePriorTransferWasReversed() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, entryRepository, commissionRuleService);
        SupplierSettlement settlement = settlement("transfer-2");
        LedgerJournal payout = payoutJournal("PAYOUT_SETTLED_1", "transfer-1", "Confirmed supplier payout transfer-1");
        LedgerJournal reversal = payoutJournal("PAYOUT_REVERSED_1", "transfer-1",
                "Reversal of confirmed supplier payout transfer-1");
        LedgerAccount payable = account(LedgerAccountType.ORGANIZATION_PAYABLE);
        LedgerAccount clearing = account(LedgerAccountType.PROVIDER_CLEARING);
        when(journalRepository.findBySourceTypeAndSourceIdOrderByCreatedAtAsc(
                "SUPPLIER_SETTLEMENT", settlement.getId())).thenReturn(java.util.List.of(payout, reversal));
        when(accountRepository.findOrganizationAccount(LedgerAccountType.ORGANIZATION_PAYABLE,
                "KES", settlement.getSupplierOrganization().getId())).thenReturn(Optional.of(payable));
        when(accountRepository.findPlatformAccount(LedgerAccountType.PROVIDER_CLEARING, "KES"))
                .thenReturn(Optional.of(clearing));

        service.recordSupplierPayout(settlement);

        org.mockito.ArgumentCaptor<LedgerJournal> captured = org.mockito.ArgumentCaptor.forClass(LedgerJournal.class);
        verify(journalRepository).save(captured.capture());
        assertThat(captured.getValue().getEventType()).isEqualTo("PAYOUT_SETTLED_2");
        assertThat(captured.getValue().getProviderReference()).isEqualTo("transfer-2");
    }

    @Test
    void replayingTheSamePayoutReversalDoesNotCreateAnotherJournal() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, entryRepository, commissionRuleService);
        SupplierSettlement settlement = settlement("transfer-1");
        LedgerJournal payout = payoutJournal("PAYOUT_SETTLED_1", "transfer-1", "Confirmed supplier payout transfer-1");
        LedgerJournal reversal = payoutJournal("PAYOUT_REVERSED_1", "transfer-1",
                "Reversal of confirmed supplier payout transfer-1");
        when(journalRepository.findBySourceTypeAndSourceIdOrderByCreatedAtAsc(
                "SUPPLIER_SETTLEMENT", settlement.getId())).thenReturn(java.util.List.of(payout, reversal));

        service.recordSupplierPayoutReversal(settlement);

        verify(journalRepository, never()).save(any(LedgerJournal.class));
    }

    @Test
    void customerCaptureCreditsTheDistributorTheFullAmount() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, entryRepository, commissionRuleService);
        Organization distributor = new Organization();
        distributor.setId(UUID.randomUUID());
        distributor.setName("Builder Depot");
        UUID paymentId = UUID.randomUUID();
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_CAPTURED")).thenReturn(Optional.empty());
        when(accountRepository.findPlatformAccount(LedgerAccountType.PROVIDER_CLEARING, "KES"))
                .thenReturn(Optional.of(account(LedgerAccountType.PROVIDER_CLEARING)));
        when(accountRepository.findOrganizationAccount(
                LedgerAccountType.ORGANIZATION_PAYABLE, "KES", distributor.getId()))
                .thenReturn(Optional.of(account(LedgerAccountType.ORGANIZATION_PAYABLE)));
        when(journalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordCustomerOrderCapture(paymentId, distributor, new BigDecimal("1250.00"), "KES");

        ArgumentCaptor<LedgerJournal> saved = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(journalRepository).save(saved.capture());
        assertThat(saved.getValue().getEntries()).hasSize(2);
        verifyNoInteractions(commissionRuleService);
    }

    @Test
    void reverseWithoutACaptureJournalIsANoOpForLegacyPayments() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, entryRepository, commissionRuleService);
        UUID paymentId = UUID.randomUUID();
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_REVERSED")).thenReturn(Optional.empty());
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_REFUNDED")).thenReturn(Optional.empty());
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_CAPTURED")).thenReturn(Optional.empty());

        service.reverseCapturedPayment("CUSTOMER_ORDER_PAYMENT", paymentId);

        verify(journalRepository, never()).save(any(LedgerJournal.class));
    }

    @Test
    void refundWithoutACaptureJournalIsANoOpForLegacyPayments() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, entryRepository, commissionRuleService);
        UUID paymentId = UUID.randomUUID();
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_REFUNDED")).thenReturn(Optional.empty());
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_REVERSED")).thenReturn(Optional.empty());
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(
                "CUSTOMER_ORDER_PAYMENT", paymentId, "PAYMENT_CAPTURED")).thenReturn(Optional.empty());

        service.recordRefund("CUSTOMER_ORDER_PAYMENT", paymentId);

        verify(journalRepository, never()).save(any(LedgerJournal.class));
    }

    private static SupplierSettlement settlement(String payoutReference) {
        Organization supplier = new Organization();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Supplier");
        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setSupplierOrganization(supplier);
        settlement.setAmount(new BigDecimal("100.00"));
        settlement.setCurrency("KES");
        settlement.setPayoutReference(payoutReference);
        return settlement;
    }

    private static LedgerJournal payoutJournal(String event, String reference, String description) {
        LedgerJournal journal = new LedgerJournal();
        journal.setEventType(event);
        journal.setProviderReference(reference);
        journal.setCurrency("KES");
        journal.setDescription(description);
        return journal;
    }

    private static LedgerJournal captureJournal() {
        LedgerJournal journal = new LedgerJournal();
        journal.setCurrency("KES");
        journal.setCommissionRuleId(UUID.randomUUID());
        journal.addEntry(entry(LedgerAccountType.PROVIDER_CLEARING, LedgerEntryDirection.DEBIT, "100.00"));
        journal.addEntry(entry(LedgerAccountType.ORGANIZATION_PAYABLE, LedgerEntryDirection.CREDIT, "95.00"));
        journal.addEntry(entry(LedgerAccountType.PLATFORM_REVENUE, LedgerEntryDirection.CREDIT, "5.00"));
        return journal;
    }

    private static LedgerEntry entry(LedgerAccountType type, LedgerEntryDirection direction, String amount) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccount(account(type));
        entry.setDirection(direction);
        entry.setAmount(new BigDecimal(amount));
        return entry;
    }

    private static LedgerAccount account(LedgerAccountType type) {
        LedgerAccount account = new LedgerAccount();
        account.setAccountType(type);
        account.setCurrency("KES");
        return account;
    }
}
