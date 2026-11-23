package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.PaymentAllocation;
import com.example.distrobackend.Domain.entity.PaymentRefund;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.CheckoutStatus;
import com.example.distrobackend.Domain.enums.RefundStatus;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreatePaymentRefundRequest;
import com.example.distrobackend.dto.PaymentRefundResponse;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.PaymentAllocationRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundService {
    private final CheckoutPaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final SupplierSettlementRepository settlementRepository;
    private final PaymentRefundRepository refundRepository;
    private final UserRepository userRepository;
    private final PaystackPaymentService paystack;
    private final FinancialLedgerService ledgerService;
    private final CentralizedCheckoutService checkoutService;

    @Transactional
    public PaymentRefundResponse requestFullRefund(AuthenticatedUser actor, UUID paymentId,
                                                  CreatePaymentRefundRequest request) {
        requireSuperAdmin(actor);
        CheckoutPayment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        PaymentRefund existing = refundRepository.findTopByPayment_IdOrderByCreatedAtDescIdDesc(paymentId).orElse(null);
        if (existing != null && existing.getStatus() != RefundStatus.FAILED) {
            return PaymentRefundResponse.from(existing);
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Refund reason is required");
        }
        if (payment.getProvider() != com.example.distrobackend.Domain.enums.PaymentProvider.PAYSTACK
                || payment.getStatus() != CheckoutPaymentStatus.SUCCESS || payment.isDemo()) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only a successful, non-demo Paystack payment can be refunded");
        }
        List<PaymentAllocation> allocations = allocationRepository
                .findByPayment_IdOrderByCreatedAtAsc(paymentId);
        if (allocations.isEmpty()) throw new ApiException(ErrorCode.CONFLICT, "Payment has no allocations to reverse");
        List<SupplierSettlement> settlements = settlementRepository.findByPaymentForUpdate(paymentId);
        if (settlements.size() != allocations.size() || settlements.stream().anyMatch(settlement ->
                settlement.getStatus() != SupplierSettlementStatus.PENDING || settlement.getSettlementBatch() != null)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Refund is available only before any supplier settlement has been batched or paid");
        }

        checkoutService.prepareRefund(payment);

        PaymentRefund refund = new PaymentRefund();
        refund.setPayment(payment);
        refund.setRequestedBy(userRepository.getReferenceById(actor.userId()));
        refund.setProviderReference("lf_refund_" + UUID.randomUUID().toString().replace("-", ""));
        refund.setAmount(payment.getAmount());
        refund.setCurrency(payment.getCurrency());
        refund.setReason(request.reason().trim());
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setNextReconciliationAt(OffsetDateTime.now().plusMinutes(1));
        refund = refundRepository.saveAndFlush(refund);
        PaystackPaymentService.Refund providerRefund = null;
        try {
            providerRefund = paystack.createRefund(refund.getProviderReference(),
                    payment.getProviderReference(), refund.getAmount(), refund.getCurrency(),
                    refund.getReason(), "LogiFlow full checkout refund " + payment.getCheckout().getCheckoutReference());
        } catch (PaystackPaymentService.ProviderOutcomeUnknownException ex) {
            refund.setStatus(RefundStatus.PENDING);
            refund.setFailureReason("Paystack refund outcome is unknown; await webhook or reconciliation");
        } catch (ApiException ex) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason(ex.getMessage());
            restorePaymentIfLatestAttempt(refund);
        }
        if (providerRefund != null) {
            applyProviderStatus(refund, providerRefund.status(), providerRefund.providerRefundId(),
                    providerRefund.amountMinor(), providerRefund.currency(), allocations, settlements);
        }
        return PaymentRefundResponse.from(refundRepository.save(refund));
    }

    @Transactional(readOnly = true)
    public Page<PaymentRefundResponse> list(AuthenticatedUser actor, Pageable pageable) {
        requireSuperAdmin(actor);
        return refundRepository.findAllByOrderByCreatedAtDesc(pageable).map(PaymentRefundResponse::from);
    }

    @Transactional
    public List<RefundCandidate> claimDue(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        List<PaymentRefund> due = refundRepository.lockDue(now, PageRequest.of(0, limit));
        List<RefundCandidate> candidates = new ArrayList<>();
        for (PaymentRefund refund : due) {
            refund.setReconciliationAttempts(refund.getReconciliationAttempts() + 1);
            refund.setNextReconciliationAt(now.plusSeconds(Math.min(21_600L,
                    60L << Math.min(refund.getReconciliationAttempts() - 1, 8))));
            candidates.add(new RefundCandidate(refund.getId(), refund.getProviderReference()));
        }
        return candidates;
    }

    public void reconcile(RefundCandidate candidate) {
        try {
            PaystackPaymentService.Refund providerRefund = paystack.verifyRefund(candidate.reference());
            processVerifiedRefund(candidate.id(), providerRefund);
        } catch (Exception ex) {
            log.warn("Paystack refund reconciliation failed for refund {}", candidate.id(), ex);
        }
    }

    @Transactional
    public void processVerifiedRefund(UUID refundId, PaystackPaymentService.Refund providerRefund) {
        PaymentRefund refund = refundRepository.findByProviderReferenceForUpdate(providerRefund.reference())
                .filter(found -> found.getId().equals(refundId)).orElse(null);
        if (refund == null) return;
        CheckoutPayment payment = paymentRepository.findByIdForUpdate(refund.getPayment().getId()).orElse(null);
        if (payment == null) return;
        applyProviderStatus(refund, providerRefund.status(), providerRefund.providerRefundId(),
                providerRefund.amountMinor(), providerRefund.currency(),
                allocationRepository.findByPayment_IdOrderByCreatedAtAsc(payment.getId()),
                settlementRepository.findByPaymentForUpdate(payment.getId()));
        refundRepository.save(refund);
    }

    @Transactional
    public void processWebhook(PaystackPaymentService.Webhook webhook) {
        RefundStatus status = switch (webhook.event()) {
            case "refund.pending" -> RefundStatus.PENDING;
            case "refund.processing" -> RefundStatus.PROCESSING;
            case "refund.processed" -> RefundStatus.PROCESSED;
            case "refund.failed" -> RefundStatus.FAILED;
            default -> null;
        };
        if (status == null) return;
        PaymentRefund refund = refundRepository.findByProviderReferenceForUpdate(webhook.reference()).orElse(null);
        if (refund == null) return;
        CheckoutPayment payment = paymentRepository.findByIdForUpdate(refund.getPayment().getId()).orElse(null);
        if (payment == null) return;
        List<PaymentAllocation> allocations = allocationRepository
                .findByPayment_IdOrderByCreatedAtAsc(payment.getId());
        List<SupplierSettlement> settlements = settlementRepository.findByPaymentForUpdate(payment.getId());
        String previousStatus = refund.getStatus().name();
        applyProviderStatus(refund, status.name().toLowerCase(java.util.Locale.ROOT),
                null, webhook.amountMinor(), webhook.currency(), allocations, settlements, previousStatus);
        refundRepository.save(refund);
    }

    private void applyProviderStatus(PaymentRefund refund, String providerStatus, String providerRefundId,
                                     Long amountMinor, String currency, List<PaymentAllocation> allocations,
                                     List<SupplierSettlement> settlements) {
        applyProviderStatus(refund, providerStatus, providerRefundId, amountMinor, currency,
                allocations, settlements, refund.getStatus().name());
    }

    private void applyProviderStatus(PaymentRefund refund, String providerStatus, String providerRefundId,
                                     Long amountMinor, String currency, List<PaymentAllocation> allocations,
                                     List<SupplierSettlement> settlements, String previousStatus) {
        RefundStatus status = switch (providerStatus.toLowerCase(java.util.Locale.ROOT)) {
            case "pending" -> RefundStatus.PENDING;
            case "processing" -> RefundStatus.PROCESSING;
            case "processed" -> RefundStatus.PROCESSED;
            case "failed", "needs-attention" -> RefundStatus.FAILED;
            default -> RefundStatus.PENDING;
        };
        if (RefundStatus.PROCESSED.name().equals(previousStatus)) return;
        if (providerRefundId != null) refund.setProviderRefundId(providerRefundId);
        if (amountMinor != null && (amountMinor != toMinor(refund.getAmount())
                || currency == null || !refund.getCurrency().equalsIgnoreCase(currency))) {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureReason("Paystack refund amount or currency did not match the requested refund");
            restorePaymentIfLatestAttempt(refund);
            return;
        }
        refund.setStatus(status);
        refund.setFailureReason(status == RefundStatus.FAILED ? "Paystack reported a failed refund" : null);
        if (status == RefundStatus.PENDING || status == RefundStatus.PROCESSING) {
            refund.setNextReconciliationAt(OffsetDateTime.now().plusMinutes(1));
        }
        if (status == RefundStatus.FAILED) {
            restorePaymentIfLatestAttempt(refund);
        }
        if (status != RefundStatus.PROCESSED) return;
        if (settlements.stream().anyMatch(settlement -> settlement.getStatus() != SupplierSettlementStatus.PENDING
                || settlement.getSettlementBatch() != null)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Cannot finalize refund after supplier settlement has started");
        }
        checkoutService.completeRefund(refund.getPayment());
        for (PaymentAllocation allocation : allocations) {
            ledgerService.recordRefund("CHECKOUT_ALLOCATION", allocation.getId());
        }
        settlements.forEach(settlement -> {
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason("Customer payment refunded before supplier payout");
        });
        settlementRepository.saveAll(settlements);
        refund.getPayment().setStatus(CheckoutPaymentStatus.REFUNDED);
        refund.getPayment().getCheckout().setStatus(CheckoutStatus.REFUNDED);
        refund.getPayment().setFailureReason("Paystack refund processed");
        refund.setProcessedAt(OffsetDateTime.now());
    }

    private static long toMinor(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Refund amount must have at most two decimals");
        }
    }

    private void restorePaymentIfLatestAttempt(PaymentRefund refund) {
        boolean latestAttempt = refundRepository
                .findTopByPayment_IdOrderByCreatedAtDescIdDesc(refund.getPayment().getId())
                .map(latest -> latest.getId().equals(refund.getId()))
                .orElse(false);
        if (latestAttempt && refund.getPayment().getStatus() == CheckoutPaymentStatus.REFUND_PENDING) {
            refund.getPayment().setStatus(CheckoutPaymentStatus.SUCCESS);
            refund.getPayment().getCheckout().setStatus(CheckoutStatus.PAID);
        }
    }

    private static void requireSuperAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != com.example.distrobackend.Domain.enums.UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    public record RefundCandidate(UUID id, String reference) {}
}
