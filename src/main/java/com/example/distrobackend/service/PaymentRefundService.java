package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PaymentRefund;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreatePaymentRefundRequest;
import com.example.distrobackend.dto.PaymentRefundResponse;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRefundService {
    private final PaymentRefundRepository refundRepository;
    private final PaymentRefundIntentService intentService;
    private final PaymentRefundFinalizationService finalizationService;
    private final PaystackPaymentService paystack;

    /** The committed intent and payment hold always precede the external provider call. */
    public PaymentRefundResponse requestFullRefund(AuthenticatedUser actor, UUID paymentId,
                                                   CreatePaymentRefundRequest request) {
        requireSuperAdmin(actor);
        PaymentRefundIntentService.PreparedRefund prepared = intentService.prepare(actor, paymentId, request);
        if (!prepared.shouldDispatch()) return prepared.response();

        PaymentRefundIntentService.Dispatch dispatch = prepared.dispatch();
        PaystackPaymentService.Refund evidence;
        try {
            evidence = paystack.createRefund(dispatch.providerReference(),
                    dispatch.transactionReference(), dispatch.amount(), dispatch.currency(),
                    dispatch.customerNote(), dispatch.merchantNote());
        } catch (PaystackPaymentService.ProviderOutcomeUnknownException ex) {
            finalizationService.markOutcomeUnknown(dispatch.refundId(),
                    "Paystack refund outcome is unknown; the stable reference will be reconciled");
            return finalizationService.getResponse(dispatch.refundId());
        } catch (ApiException ex) {
            PaymentRefundResponse response = finalizationService.markDefinitiveFailure(dispatch.refundId(), ex.getMessage());
            return response == null ? prepared.response() : response;
        }
        // If local persistence fails after provider acceptance, the committed REQUESTED
        // intent remains eligible for verification under the same provider reference.
        PaymentRefundResponse response = finalizationService.applyProviderRefund(dispatch.refundId(), evidence);
        return response == null ? prepared.response() : response;
    }

    @Transactional(readOnly = true)
    public Page<PaymentRefundResponse> list(AuthenticatedUser actor, Pageable pageable) {
        requireSuperAdmin(actor);
        return refundRepository.findAllByOrderByCreatedAtDesc(pageable).map(PaymentRefundResponse::from);
    }

    @Transactional
    public List<RefundCandidate> claimDue(int limit) {
        OffsetDateTime now = OffsetDateTime.now();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<PaymentRefund> due = refundRepository.lockDue(now, PageRequest.of(0, safeLimit));
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
            PaystackPaymentService.Refund evidence = paystack.verifyRefund(candidate.reference());
            finalizationService.applyProviderRefund(candidate.id(), evidence);
        } catch (Exception ex) {
            log.warn("Paystack refund reconciliation failed for refund {}; the payment remains held",
                    candidate.id(), ex);
        }
    }

    public void processWebhook(PaystackPaymentService.Webhook webhook) {
        finalizationService.processWebhook(webhook);
    }

    private static void requireSuperAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != com.example.distrobackend.Domain.enums.UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    public record RefundCandidate(UUID id, String reference) {}
}
