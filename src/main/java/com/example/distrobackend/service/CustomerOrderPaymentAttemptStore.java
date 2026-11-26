package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CustomerPaymentAttempt;
import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.CustomerPaymentAttemptStatus;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.repository.CustomerPaymentAttemptRepository;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Persists Paystack customer-order payment intent before making the provider call. */
@Service
@RequiredArgsConstructor
public class CustomerOrderPaymentAttemptStore {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerPaymentAttemptRepository attemptRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StartResult start(UUID orderId, String reference, String mode, BigDecimal amount,
                             String currency, String idempotencyKey) {
        Order order = orderRepository.findByIdWithOwnershipForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Only pending orders can be paid");
        }
        if (idempotencyKey != null) {
            CustomerPaymentAttempt keyed = attemptRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (keyed != null) {
                if (!keyed.getPayment().getOrder().getId().equals(orderId)) {
                    throw new ApiException(ErrorCode.CONFLICT, "Idempotency key has already been used");
                }
                return new StartResult(keyed.getPayment().getId(), keyed.getId(), false);
            }
        }

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (payment != null && (payment.getStatus() == PaymentStatus.CONFIRMED
                || payment.getStatus() == PaymentStatus.RECONCILED)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Order is already paid");
        }
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING
                && payment.getProvider() == PaymentProvider.PAYSTACK) {
            CustomerPaymentAttempt current = attemptRepository
                    .findFirstByPayment_IdOrderByAttemptNumberDesc(payment.getId()).orElse(null);
            if (current != null && (current.getStatus() == CustomerPaymentAttemptStatus.INITIALIZING
                    || current.getStatus() == CustomerPaymentAttemptStatus.PENDING
                    || current.getStatus() == CustomerPaymentAttemptStatus.OUTCOME_UNKNOWN)) {
                return new StartResult(payment.getId(), current.getId(), false);
            }
        }
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Another payment provider already has an unresolved payment for this order");
        }
        if (idempotencyKey != null) {
            Payment keyed = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (keyed != null && !keyed.getOrder().getId().equals(orderId)) {
                throw new ApiException(ErrorCode.CONFLICT, "Idempotency key has already been used");
            }
        }

        if (payment == null) {
            payment = new Payment();
            payment.setOrder(order);
        }
        int attemptNumber = payment.getId() == null ? 1
                : attemptRepository.findFirstByPayment_IdOrderByAttemptNumberDesc(payment.getId())
                        .map(CustomerPaymentAttempt::getAttemptNumber).orElse(0) + 1;
        payment.setMethod(PaymentMethod.CARD);
        payment.setProvider(PaymentProvider.PAYSTACK);
        payment.setProviderReference(reference);
        payment.setProviderAuthorizationUrl(null);
        payment.setProviderMode(mode);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setStatus(PaymentStatus.INITIATING);
        payment.setInitiatedAt(OffsetDateTime.now());
        payment.setConfirmedAt(null);
        payment.setFailureReason(null);
        payment.setMpesaCheckoutRequestId(null);
        payment.setMpesaMerchantRequestId(null);
        payment.setMpesaReceiptNumber(null);
        payment.setPhoneNumber(null);
        payment.setIdempotencyKey(idempotencyKey);
        payment = paymentRepository.saveAndFlush(payment);

        CustomerPaymentAttempt attempt = new CustomerPaymentAttempt();
        attempt.setPayment(payment);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setProviderReference(reference);
        attempt.setProviderMode(mode);
        attempt.setStatus(CustomerPaymentAttemptStatus.INITIALIZING);
        attempt.setAmount(amount);
        attempt.setCurrency(currency);
        attempt.setIdempotencyKey(idempotencyKey);
        attempt.setNextVerificationAt(OffsetDateTime.now().plusMinutes(1));
        attempt = attemptRepository.saveAndFlush(attempt);
        return new StartResult(payment.getId(), attempt.getId(), true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAccepted(UUID attemptId, PaystackPaymentService.Initialization initialization) {
        CustomerPaymentAttempt attempt = requireAttempt(attemptId);
        if (!active(attempt.getStatus())) return;
        Payment payment = paymentRepository.findByIdForUpdate(attempt.getPayment().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        attempt.setStatus(CustomerPaymentAttemptStatus.PENDING);
        attempt.setAuthorizationUrl(initialization.authorizationUrl());
        attempt.setAccessCode(initialization.accessCode());
        attempt.setNextVerificationAt(OffsetDateTime.now().plusMinutes(2));
        attempt.setFailureReason(null);
        payment.setProviderReference(attempt.getProviderReference());
        payment.setProviderAuthorizationUrl(initialization.authorizationUrl());
        payment.setProviderMode(attempt.getProviderMode());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setFailureReason(null);
        attemptRepository.saveAndFlush(attempt);
        paymentRepository.saveAndFlush(payment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOutcomeUnknown(UUID attemptId, String reason) {
        CustomerPaymentAttempt attempt = requireAttempt(attemptId);
        if (!active(attempt.getStatus())) return;
        attempt.setStatus(CustomerPaymentAttemptStatus.OUTCOME_UNKNOWN);
        attempt.setFailureReason(reason);
        attempt.setNextVerificationAt(OffsetDateTime.now().plusMinutes(1));
        Payment payment = paymentRepository.findByIdForUpdate(attempt.getPayment().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        if (attempt.getProviderReference().equals(payment.getProviderReference())) {
            payment.setStatus(PaymentStatus.PENDING);
            payment.setProviderMode(attempt.getProviderMode());
            payment.setFailureReason("Paystack is verifying the payment outcome; do not submit another payment yet");
            paymentRepository.saveAndFlush(payment);
        }
        attemptRepository.saveAndFlush(attempt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID attemptId, String reason) {
        CustomerPaymentAttempt attempt = requireAttempt(attemptId);
        if (!active(attempt.getStatus())) return;
        attempt.setStatus(CustomerPaymentAttemptStatus.FAILED);
        attempt.setFailureReason(reason);
        attempt.setCompletedAt(OffsetDateTime.now());
        attempt.setNextVerificationAt(null);
        Payment payment = paymentRepository.findByIdForUpdate(attempt.getPayment().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        if (attempt.getProviderReference().equals(payment.getProviderReference())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(reason);
            payment.setProviderAuthorizationUrl(null);
            paymentRepository.saveAndFlush(payment);
        }
        attemptRepository.saveAndFlush(attempt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleVerificationRetry(UUID attemptId, String reason) {
        CustomerPaymentAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
        if (attempt == null || !active(attempt.getStatus())) return;
        int count = attempt.getVerificationAttempts() + 1;
        attempt.setVerificationAttempts(count);
        attempt.setLastVerifiedAt(OffsetDateTime.now());
        attempt.setFailureReason(reason);
        long delaySeconds = Math.min(21_600L, 60L << Math.min(count, 8));
        attempt.setNextVerificationAt(OffsetDateTime.now().plusSeconds(delaySeconds));
        attemptRepository.saveAndFlush(attempt);
    }

    private CustomerPaymentAttempt requireAttempt(UUID id) {
        return attemptRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payment attempt not found"));
    }

    private static boolean active(CustomerPaymentAttemptStatus status) {
        return status == CustomerPaymentAttemptStatus.INITIALIZING
                || status == CustomerPaymentAttemptStatus.PENDING
                || status == CustomerPaymentAttemptStatus.OUTCOME_UNKNOWN;
    }

    public record StartResult(UUID paymentId, UUID attemptId, boolean created) {}
}
