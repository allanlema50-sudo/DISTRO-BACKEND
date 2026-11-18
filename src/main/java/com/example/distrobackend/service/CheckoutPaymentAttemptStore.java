package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Checkout;
import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.CheckoutStatus;
import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Commits checkout payment-attempt state independently of Daraja network calls. */
@Service
@RequiredArgsConstructor
public class CheckoutPaymentAttemptStore {
    private final CheckoutRepository checkoutRepository;
    private final CheckoutPaymentRepository checkoutPaymentRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StartResult start(UUID checkoutId, UUID buyerOrganizationId, PaymentMethod method,
                             BigDecimal amount, String currency, boolean demo, String demoLabel,
                             String idempotencyKey) {
        Checkout checkout = checkoutRepository.findByIdAndBuyerOrganization_IdForUpdate(checkoutId, buyerOrganizationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout not found"));
        CheckoutPayment existing = checkoutPaymentRepository.findByCheckout_Id(checkoutId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == CheckoutPaymentStatus.PENDING
                    || existing.getStatus() == CheckoutPaymentStatus.SUCCESS) {
                return new StartResult(existing.getId(), false);
            }
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Checkout already has a terminal payment attempt");
        }
        if (checkout.getStatus() != CheckoutStatus.PAYMENT_PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Checkout is not payable");
        }

        CheckoutPayment payment = new CheckoutPayment();
        payment.setCheckout(checkout);
        payment.setMethod(method);
        payment.setStatus(CheckoutPaymentStatus.PENDING);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setDemo(demo);
        payment.setDemoLabel(demoLabel);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setProviderReference(demo ? "DEMO-PENDING-" + UUID.randomUUID() : null);
        payment.setInitiatedAt(OffsetDateTime.now());
        payment = checkoutPaymentRepository.saveAndFlush(payment);
        return new StartResult(payment.getId(), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAccepted(UUID paymentId, String checkoutRequestId, String merchantRequestId,
                             String responseDescription) {
        CheckoutPayment payment = requirePayment(paymentId);
        payment.setMpesaCheckoutRequestId(checkoutRequestId);
        payment.setMpesaMerchantRequestId(merchantRequestId);
        payment.setProviderReference(checkoutRequestId);
        payment.setProviderPayload(Map.of("responseDescription", responseDescription));
        checkoutPaymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOutcomeUnknown(UUID paymentId) {
        CheckoutPayment payment = requirePayment(paymentId);
        // Leave this attempt PENDING so retries cannot create another STK push.
        // The callback URL carries paymentId and can reconcile it if Daraja
        // accepted the request before the HTTP response timed out.
        payment.setProviderPayload(Map.of("state", "REQUEST_OUTCOME_UNKNOWN"));
        checkoutPaymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID paymentId, String reason) {
        CheckoutPayment payment = requirePayment(paymentId);
        payment.setStatus(CheckoutPaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment.setConfirmedAt(OffsetDateTime.now());
        checkoutPaymentRepository.saveAndFlush(payment);
    }

    private CheckoutPayment requirePayment(UUID paymentId) {
        return checkoutPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
    }

    public record StartResult(UUID paymentId, boolean created) {}
}
