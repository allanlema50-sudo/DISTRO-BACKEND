package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.Checkout;
import com.example.distrobackend.Domain.entity.LedgerAccount;
import com.example.distrobackend.Domain.entity.LedgerEntry;
import com.example.distrobackend.Domain.entity.LedgerJournal;
import com.example.distrobackend.Domain.entity.PaymentAllocation;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.LedgerAccountType;
import com.example.distrobackend.Domain.enums.LedgerEntryDirection;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.LedgerJournalRepository;
import com.example.distrobackend.repository.PaymentAllocationRepository;
import com.example.distrobackend.repository.PaymentDisputeRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialReconciliationReportServiceTest {
    @Mock CheckoutPaymentRepository paymentRepository;
    @Mock PaymentAllocationRepository allocationRepository;
    @Mock SupplierSettlementRepository settlementRepository;
    @Mock PaymentRefundRepository refundRepository;
    @Mock PaymentDisputeRepository disputeRepository;
    @Mock LedgerJournalRepository journalRepository;
    @InjectMocks FinancialReconciliationReportService service;

    @Test
    void boundsPageAndUsesStableNewestFirstOrdering() {
        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN,
                null, null, null);
        OffsetDateTime from = OffsetDateTime.parse("2026-11-01T00:00:00Z");
        OffsetDateTime to = from.plusDays(7);
        when(paymentRepository.findByProviderAndDemoFalseAndStatusInAndInitiatedAtGreaterThanEqualAndInitiatedAtLessThan(
                eq(PaymentProvider.PAYSTACK), any(), eq(from), eq(to), any(Pageable.class)))
                .thenAnswer(invocation -> Page.<CheckoutPayment>empty(invocation.getArgument(4)));

        service.report(actor, from, to, PageRequest.of(0, 500));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentRepository).findByProviderAndDemoFalseAndStatusInAndInitiatedAtGreaterThanEqualAndInitiatedAtLessThan(
                eq(PaymentProvider.PAYSTACK), any(), eq(from), eq(to), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().toList()).containsExactly(
                Sort.Order.desc("initiatedAt"), Sort.Order.desc("id"));
    }

    @Test
    void readsTheReportFromOneRepeatableReadSnapshot() throws Exception {
        Transactional transactional = FinancialReconciliationReportService.class
                .getMethod("report", AuthenticatedUser.class, OffsetDateTime.class,
                        OffsetDateTime.class, Pageable.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    void flagsReversalJournalWhoseAmountDoesNotMatchItsPayout() {
        UUID paymentId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        OffsetDateTime from = OffsetDateTime.parse("2026-11-01T00:00:00Z");
        Checkout checkout = new Checkout();
        checkout.setCheckoutReference("CHK-REPORT-1");
        CheckoutPayment payment = new CheckoutPayment();
        payment.setId(paymentId);
        payment.setCheckout(checkout);
        payment.setStatus(CheckoutPaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("KES");
        payment.setInitiatedAt(from);
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setId(allocationId);
        allocation.setPayment(payment);
        allocation.setGrossAmount(new BigDecimal("100.00"));
        SupplierSettlement settlement = new SupplierSettlement();
        settlement.setId(settlementId);
        settlement.setAllocation(allocation);
        settlement.setAmount(new BigDecimal("100.00"));
        settlement.setCurrency("KES");
        settlement.setStatus(SupplierSettlementStatus.REVERSED);

        LedgerJournal payout = journal("PAYOUT_SETTLED_1", "100.00");
        LedgerJournal reversal = journal("PAYOUT_REVERSED_1", "50.00");
        payout.setSourceId(settlementId);
        reversal.setSourceId(settlementId);
        when(paymentRepository.findByProviderAndDemoFalseAndStatusInAndInitiatedAtGreaterThanEqualAndInitiatedAtLessThan(
                eq(PaymentProvider.PAYSTACK), any(), eq(from), eq(from.plusDays(1)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 100), 1));
        when(allocationRepository.findByPayment_IdInOrderByCreatedAtAsc(List.of(paymentId)))
                .thenReturn(List.of(allocation));
        when(settlementRepository.findByAllocation_Payment_IdIn(List.of(paymentId)))
                .thenReturn(List.of(settlement));
        when(refundRepository.findByPayment_IdInOrderByCreatedAtDesc(List.of(paymentId))).thenReturn(List.of());
        when(disputeRepository.findByPayment_IdIn(List.of(paymentId))).thenReturn(List.of());
        when(journalRepository.findBySourceTypeAndSourceIdInAndEventTypeIn(
                "CHECKOUT_ALLOCATION", List.of(allocationId), java.util.Set.of("PAYMENT_CAPTURED", "PAYMENT_REFUNDED")))
                .thenReturn(List.of());
        when(journalRepository.findBySourceTypeAndSourceIdIn("SUPPLIER_SETTLEMENT", List.of(settlementId)))
                .thenReturn(List.of(payout, reversal));

        var result = service.report(new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN,
                        null, null, null), from, from.plusDays(1), PageRequest.of(0, 100));

        assertThat(result.getContent()).singleElement()
                .satisfies(item -> assertThat(item.issues()).contains("PAYOUT_REVERSAL_AMOUNT_MISMATCH"));
    }

    private static LedgerJournal journal(String event, String amount) {
        LedgerJournal journal = new LedgerJournal();
        journal.setEventType(event);
        journal.setCurrency("KES");
        journal.addEntry(entry(LedgerAccountType.PROVIDER_CLEARING, LedgerEntryDirection.DEBIT, amount));
        journal.addEntry(entry(LedgerAccountType.PROVIDER_CLEARING, LedgerEntryDirection.CREDIT, amount));
        return journal;
    }

    private static LedgerEntry entry(LedgerAccountType accountType, LedgerEntryDirection direction, String amount) {
        LedgerAccount account = new LedgerAccount();
        account.setAccountType(accountType);
        LedgerEntry entry = new LedgerEntry();
        entry.setAccount(account);
        entry.setDirection(direction);
        entry.setAmount(new BigDecimal(amount));
        return entry;
    }
}
