package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Checkout;
import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.CheckoutStatus;
import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Domain.enums.PaymentProvider;
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
        if (idempotencyKey != null) {
            CheckoutPayment keyed = checkoutPaymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (keyed != null) {
                if (!keyed.getCheckout().getId().equals(checkoutId)) throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Idempotency-Key was already used for another payment attempt");
                return new StartResult(keyed.getId(), false);
            }
        }
        CheckoutPayment existing = checkoutPaymentRepository.findFirstByCheckout_IdOrderByInitiatedAtDesc(checkoutId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == CheckoutPaymentStatus.PENDING || existing.getStatus() == CheckoutPaymentStatus.UNKNOWN
                    || existing.getStatus() == CheckoutPaymentStatus.SUCCESS) {
                return new StartResult(existing.getId(), false);
            }
            if (!java.util.Set.of(CheckoutPaymentStatus.FAILED, CheckoutPaymentStatus.CANCELLED,
                    CheckoutPaymentStatus.TIMED_OUT).contains(existing.getStatus())) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Previous payment attempt is unresolved");
            }
        }
        if (!java.util.Set.of(CheckoutStatus.PAYMENT_PENDING, CheckoutStatus.PAYMENT_FAILED,
                CheckoutStatus.UNKNOWN).contains(checkout.getStatus())) {
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
        payment.setProvider(demo ? PaymentProvider.DEMO : null);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setProviderReference(demo ? "DEMO-PENDING-" + UUID.randomUUID() : null);
        payment.setInitiatedAt(OffsetDateTime.now());
        payment = checkoutPaymentRepository.saveAndFlush(payment);
        checkout.setStatus(CheckoutStatus.PAYMENT_PENDING);
        checkout.setPayment(payment);
        checkoutRepository.save(checkout);
        return new StartResult(payment.getId(), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAccepted(UUID paymentId, String checkoutRequestId, String merchantRequestId,
                             String responseDescription) {
        CheckoutPayment payment = requirePayment(paymentId);
        payment.setProvider(PaymentProvider.DARAJA);
        payment.setMpesaCheckoutRequestId(checkoutRequestId);
        payment.setMpesaMerchantRequestId(merchantRequestId);
        payment.setProviderReference(checkoutRequestId);
        payment.setProviderPayload(Map.of("responseDescription", responseDescription));
        payment.setProviderNextVerificationAt(null);
        checkoutPaymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void prepareDaraja(UUID paymentId) {
        CheckoutPayment payment = requirePayment(paymentId);
        payment.setProvider(PaymentProvider.DARAJA);
        payment.setProviderPayload(Map.of("provider", PaymentProvider.DARAJA.name(), "state", "INITIALIZATION_STARTED"));
        checkoutPaymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void preparePaystack(UUID paymentId, String providerReference) {
        CheckoutPayment payment = requirePayment(paymentId);
        payment.setProvider(PaymentProvider.PAYSTACK);
        payment.setProviderReference(providerReference);
        payment.setProviderPayload(Map.of("provider", PaymentProvider.PAYSTACK.name(), "state", "INITIALIZATION_STARTED"));
        payment.setProviderVerificationAttempts(0);
        payment.setProviderNextVerificationAt(OffsetDateTime.now().plusMinutes(1));
        payment.setProviderLastVerificationError(null);
        checkoutPaymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPaystackAccepted(UUID paymentId, String providerReference,
                                     String authorizationUrl, String accessCode,
                                     String channel) {
        CheckoutPayment payment = checkoutPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        if (payment.getStatus() != CheckoutPaymentStatus.PENDING
                && payment.getStatus() != CheckoutPaymentStatus.UNKNOWN) return;
        payment.setProvider(PaymentProvider.PAYSTACK);
        payment.setProviderReference(providerReference);
        payment.setProviderAuthorizationUrl(authorizationUrl);
        payment.setProviderAccessCode(accessCode);
        payment.setProviderPayload(Map.of("provider", PaymentProvider.PAYSTACK.name(),
                "channel", channel, "state", "ACCEPTED"));
        payment.setProviderNextVerificationAt(OffsetDateTime.now().plusMinutes(2));
        payment.setProviderLastVerificationError(null);
        checkoutPaymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOutcomeUnknown(UUID paymentId) {
        CheckoutPayment payment = checkoutPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        if (payment.getStatus() != CheckoutPaymentStatus.PENDING
                && payment.getStatus() != CheckoutPaymentStatus.UNKNOWN) return;
        // UNKNOWN blocks a second charge until provider verification resolves it.
        payment.setStatus(CheckoutPaymentStatus.UNKNOWN);
        payment.setProviderPayload(Map.of("provider", payment.getProvider() == null ? "UNKNOWN" : payment.getProvider().name(),
                "state", "REQUEST_OUTCOME_UNKNOWN"));
        payment.setProviderNextVerificationAt(OffsetDateTime.now().plusMinutes(1));
        checkoutPaymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void schedulePaystackVerificationRetry(UUID paymentId, String reason) {
        CheckoutPayment payment = checkoutPaymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || payment.getProvider() != PaymentProvider.PAYSTACK
                || (payment.getStatus() != CheckoutPaymentStatus.PENDING
                && payment.getStatus() != CheckoutPaymentStatus.UNKNOWN)) return;
        int attempts = payment.getProviderVerificationAttempts() + 1;
        payment.setProviderVerificationAttempts(attempts);
        long delaySeconds = Math.min(21_600L, 60L << Math.min(attempts, 8));
        payment.setProviderNextVerificationAt(OffsetDateTime.now().plusSeconds(delaySeconds));
        payment.setProviderLastVerificationError(reason == null ? "Provider status is not final" : reason);
        checkoutPaymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireUninitializedPaystackAttempt(UUID paymentId, OffsetDateTime olderThan) {
        CheckoutPayment payment = checkoutPaymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || payment.getProvider() != PaymentProvider.PAYSTACK
                || payment.getStatus() != CheckoutPaymentStatus.UNKNOWN
                || payment.getInitiatedAt() == null || !payment.getInitiatedAt().isBefore(olderThan)
                || payment.getProviderPayload() == null
                || !"REQUEST_OUTCOME_UNKNOWN".equals(payment.getProviderPayload().get("state"))) return false;
        payment.setStatus(CheckoutPaymentStatus.TIMED_OUT);
        payment.setConfirmedAt(OffsetDateTime.now());
        payment.setFailureReason("Paystack did not create a transaction for the unresolved initialization");
        payment.setProviderNextVerificationAt(null);
        payment.getCheckout().setStatus(com.example.distrobackend.Domain.enums.CheckoutStatus.PAYMENT_FAILED);
        checkoutPaymentRepository.saveAndFlush(payment);
        checkoutRepository.save(payment.getCheckout());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID paymentId, String reason) {
        CheckoutPayment payment = checkoutPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        if (payment.getStatus() != CheckoutPaymentStatus.PENDING
                && payment.getStatus() != CheckoutPaymentStatus.UNKNOWN) return;
        payment.setStatus(CheckoutPaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment.setConfirmedAt(OffsetDateTime.now());
        payment.setProviderNextVerificationAt(null);
        payment.getCheckout().setStatus(com.example.distrobackend.Domain.enums.CheckoutStatus.PAYMENT_FAILED);
        checkoutPaymentRepository.saveAndFlush(payment);
        checkoutRepository.save(payment.getCheckout());
    }

    private CheckoutPayment requirePayment(UUID paymentId) {
        return checkoutPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
    }

    public record StartResult(UUID paymentId, boolean created) {}
}
