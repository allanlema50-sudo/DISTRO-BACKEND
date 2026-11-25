package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.PaymentAllocation;
import com.example.distrobackend.Domain.entity.PaymentRefund;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.CheckoutStatus;
import com.example.distrobackend.Domain.enums.RefundStatus;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.dto.PaymentRefundResponse;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.PaymentAllocationRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Applies provider evidence in a separate transaction from provider network calls. */
@Service
@RequiredArgsConstructor
public class PaymentRefundFinalizationService {
    private final CheckoutPaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final PaymentRefundRepository refundRepository;
    private final SupplierSettlementRepository settlementRepository;
    private final FinancialLedgerService ledgerService;
    private final CentralizedCheckoutService checkoutService;

    @Transactional
    public PaymentRefundResponse applyProviderRefund(UUID refundId, PaystackPaymentService.Refund evidence) {
        PaymentRefund refund = refundRepository.findByProviderReferenceForUpdate(evidence.reference())
                .filter(candidate -> candidate.getId().equals(refundId)).orElse(null);
        if (refund == null) return null;
        CheckoutPayment payment = paymentRepository.findByIdForUpdate(refund.getPayment().getId()).orElse(null);
        if (payment == null) return null;
        applyProviderStatus(refund, evidence.status(), evidence.providerRefundId(), evidence.amountMinor(),
                evidence.currency(), payment);
        return PaymentRefundResponse.from(refundRepository.saveAndFlush(refund));
    }

    @Transactional
    public void markOutcomeUnknown(UUID refundId, String message) {
        PaymentRefund refund = refundRepository.findByIdForUpdate(refundId).orElse(null);
        if (refund == null || refund.getStatus() == RefundStatus.PROCESSED) return;
        refund.setStatus(RefundStatus.PENDING);
        refund.setFailureReason(message);
        scheduleRetry(refund);
        refundRepository.save(refund);
    }

    @Transactional
    public PaymentRefundResponse markDefinitiveFailure(UUID refundId, String message) {
        PaymentRefund refund = refundRepository.findByIdForUpdate(refundId).orElse(null);
        if (refund == null) return null;
        CheckoutPayment payment = paymentRepository.findByIdForUpdate(refund.getPayment().getId()).orElse(null);
        if (payment == null) return null;
        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason(message);
        restorePaymentIfLatestAttempt(refund, payment);
        return PaymentRefundResponse.from(refundRepository.saveAndFlush(refund));
    }

    @Transactional(readOnly = true)
    public PaymentRefundResponse getResponse(UUID refundId) {
        return refundRepository.findById(refundId).map(PaymentRefundResponse::from).orElse(null);
    }

    @Transactional
    public void processWebhook(PaystackPaymentService.Webhook webhook) {
        RefundStatus status = switch (webhook.event()) {
            case "refund.pending" -> RefundStatus.PENDING;
            case "refund.processing" -> RefundStatus.PROCESSING;
            case "refund.needs-attention" -> RefundStatus.PROCESSING;
            case "refund.processed" -> RefundStatus.PROCESSED;
            case "refund.failed" -> RefundStatus.FAILED;
            default -> null;
        };
        if (status == null) return;
        PaymentRefund refund = refundRepository.findByProviderReferenceForUpdate(webhook.reference()).orElse(null);
        if (refund == null && webhook.transactionReference() != null) {
            refund = refundRepository.findFirstByPayment_ProviderReferenceAndStatusInOrderByCreatedAtDescIdDesc(
                    webhook.transactionReference(), List.of(RefundStatus.REQUESTED, RefundStatus.PENDING,
                            RefundStatus.PROCESSING)).orElse(null);
        }
        if (refund == null) return;
        CheckoutPayment payment = paymentRepository.findByIdForUpdate(refund.getPayment().getId()).orElse(null);
        if (payment == null) return;
        String providerStatus = "refund.needs-attention".equals(webhook.event())
                ? "needs-attention" : status.name().toLowerCase(Locale.ROOT);
        applyProviderStatus(refund, providerStatus, null,
                webhook.amountMinor(), webhook.currency(), payment);
        refundRepository.saveAndFlush(refund);
    }

    private void applyProviderStatus(PaymentRefund refund, String providerStatus, String providerRefundId,
                                     Long amountMinor, String currency, CheckoutPayment payment) {
        if (refund.getStatus() == RefundStatus.PROCESSED || refund.getStatus() == RefundStatus.FAILED) return;
        String normalized = providerStatus == null ? "" : providerStatus.toLowerCase(Locale.ROOT);
        RefundStatus status = switch (normalized) {
            case "pending" -> RefundStatus.PENDING;
            case "processing", "needs-attention" -> RefundStatus.PROCESSING;
            case "processed" -> RefundStatus.PROCESSED;
            case "failed" -> RefundStatus.FAILED;
            default -> RefundStatus.PENDING;
        };
        if (providerRefundId != null) refund.setProviderRefundId(providerRefundId);
        if (status == RefundStatus.PROCESSED && !matchesRequestedRefund(refund, amountMinor, currency)) {
            keepOnHoldForReview(refund, "Paystack refund evidence is incomplete or amount/currency mismatched");
            return;
        }
        if (normalized.equals("needs-attention")) {
            keepOnHoldForReview(refund, "Paystack requires attention to complete this refund");
            return;
        }
        refund.setStatus(status);
        if (status == RefundStatus.PENDING || status == RefundStatus.PROCESSING) {
            refund.setFailureReason(null);
            scheduleRetry(refund);
            return;
        }
        if (status == RefundStatus.FAILED) {
            refund.setFailureReason("Paystack reported a failed refund");
            restorePaymentIfLatestAttempt(refund, payment);
            return;
        }

        List<PaymentAllocation> allocations = allocationRepository
                .findByPayment_IdOrderByCreatedAtAsc(payment.getId());
        List<SupplierSettlement> settlements = settlementRepository.findByPaymentForUpdate(payment.getId());
        if (settlements.size() != allocations.size() || settlements.stream().anyMatch(settlement ->
                settlement.getStatus() != SupplierSettlementStatus.PENDING || settlement.getSettlementBatch() != null)) {
            keepOnHoldForReview(refund, "Supplier settlement changed before refund finalization");
            return;
        }
        checkoutService.completeRefund(payment);
        for (PaymentAllocation allocation : allocations) {
            ledgerService.recordRefund("CHECKOUT_ALLOCATION", allocation.getId());
        }
        settlements.forEach(settlement -> {
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason("Customer payment refunded before supplier payout");
        });
        settlementRepository.saveAll(settlements);
        payment.setStatus(CheckoutPaymentStatus.REFUNDED);
        payment.getCheckout().setStatus(CheckoutStatus.REFUNDED);
        payment.setFailureReason("Paystack refund processed");
        refund.setFailureReason(null);
        refund.setProcessedAt(OffsetDateTime.now());
    }

    private static boolean matchesRequestedRefund(PaymentRefund refund, Long amountMinor, String currency) {
        if (amountMinor == null || currency == null || !refund.getCurrency().equalsIgnoreCase(currency)) return false;
        try {
            return refund.getAmount().movePointRight(2).longValueExact() == amountMinor;
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    private static void keepOnHoldForReview(PaymentRefund refund, String reason) {
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setFailureReason(reason);
        scheduleRetry(refund);
    }

    private static void scheduleRetry(PaymentRefund refund) {
        refund.setNextReconciliationAt(OffsetDateTime.now().plusMinutes(2));
    }

    private void restorePaymentIfLatestAttempt(PaymentRefund refund, CheckoutPayment payment) {
        boolean latest = refundRepository.findTopByPayment_IdOrderByCreatedAtDescIdDesc(payment.getId())
                .map(candidate -> candidate.getId().equals(refund.getId())).orElse(false);
        if (latest && payment.getStatus() == CheckoutPaymentStatus.REFUND_PENDING) {
            payment.setStatus(CheckoutPaymentStatus.SUCCESS);
            payment.getCheckout().setStatus(CheckoutStatus.PAID);
        }
    }
}
