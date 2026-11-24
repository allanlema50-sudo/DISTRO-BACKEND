package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Checkout;
import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.PaymentRefund;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.CheckoutStatus;
import com.example.distrobackend.Domain.enums.RefundStatus;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.PaymentAllocationRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundFinalizationServiceTest {
    @Mock private CheckoutPaymentRepository paymentRepository;
    @Mock private PaymentAllocationRepository allocationRepository;
    @Mock private PaymentRefundRepository refundRepository;
    @Mock private SupplierSettlementRepository settlementRepository;
    @Mock private FinancialLedgerService ledgerService;
    @Mock private CentralizedCheckoutService checkoutService;

    private PaymentRefundFinalizationService service;
    private PaymentRefund refund;
    private CheckoutPayment payment;

    @BeforeEach
    void setUp() {
        service = new PaymentRefundFinalizationService(paymentRepository, allocationRepository,
                refundRepository, settlementRepository, ledgerService, checkoutService);
        Checkout checkout = new Checkout();
        checkout.setId(UUID.randomUUID());
        checkout.setStatus(CheckoutStatus.REFUND_PENDING);
        payment = new CheckoutPayment();
        payment.setId(UUID.randomUUID());
        payment.setCheckout(checkout);
        payment.setStatus(CheckoutPaymentStatus.REFUND_PENDING);
        refund = new PaymentRefund();
        refund.setId(UUID.randomUUID());
        refund.setProviderReference("lf_refund_01234567890123456789012345678901");
        refund.setPayment(payment);
        refund.setAmount(new BigDecimal("100.00"));
        refund.setCurrency("KES");
        refund.setStatus(RefundStatus.PENDING);
        when(refundRepository.findByProviderReferenceForUpdate(refund.getProviderReference()))
                .thenReturn(Optional.of(refund));
        when(paymentRepository.findByIdForUpdate(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRepository.saveAndFlush(any(PaymentRefund.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void needsAttentionKeepsRefundAndPaymentOnHold() {
        var result = service.applyProviderRefund(refund.getId(), new PaystackPaymentService.Refund(
                refund.getProviderReference(), "refund_1", "needs-attention", 10_000L, "KES"));

        assertThat(result.status()).isEqualTo(RefundStatus.PROCESSING.name());
        assertThat(refund.getFailureReason()).contains("requires attention");
        assertThat(payment.getStatus()).isEqualTo(CheckoutPaymentStatus.REFUND_PENDING);
        assertThat(payment.getCheckout().getStatus()).isEqualTo(CheckoutStatus.REFUND_PENDING);
        verify(checkoutService, never()).completeRefund(any());
        verify(ledgerService, never()).recordRefund(any(), any());
    }

    @Test
    void mismatchedProcessedEvidenceKeepsPaymentOnHoldForReconciliation() {
        var result = service.applyProviderRefund(refund.getId(), new PaystackPaymentService.Refund(
                refund.getProviderReference(), "refund_1", "processed", 9_000L, "KES"));

        assertThat(result.status()).isEqualTo(RefundStatus.PROCESSING.name());
        assertThat(refund.getFailureReason()).contains("mismatched");
        assertThat(payment.getStatus()).isEqualTo(CheckoutPaymentStatus.REFUND_PENDING);
        assertThat(payment.getCheckout().getStatus()).isEqualTo(CheckoutStatus.REFUND_PENDING);
        verify(settlementRepository, never()).findByPaymentForUpdate(any());
        verify(checkoutService, never()).completeRefund(any());
    }
}
