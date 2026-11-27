package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CustomerPaymentAttempt;
import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.CustomerPaymentAttemptStatus;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.PaymentInitiateRequest;
import com.example.distrobackend.dto.PaymentInitiateResponse;
import com.example.distrobackend.repository.CustomerPaymentAttemptRepository;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerOrderPaystackPaymentService {
    private static final long UNRESOLVED_REFERENCE_GRACE_MINUTES = 10;
    private static final int RECONCILIATION_BATCH_SIZE = 100;

    private final PaystackPaymentService paystackPaymentService;
    private final CustomerOrderPaymentAttemptStore attemptStore;
    private final CustomerPaymentAttemptRepository attemptRepository;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final FinancialLedgerService financialLedgerService;
    private final PlatformTransactionManager transactionManager;

    public PaymentInitiateResponse initiate(AuthenticatedUser actor, PaymentInitiateRequest request) {
        if (request == null || request.orderId() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "orderId is required");
        }
        Order order = orderRepository.findByIdWithOwnership(request.orderId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        requireOrderAccess(actor, order);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Only pending orders can be paid");
        }
        if (order.getCustomer() == null || order.getCustomer().getEmail() == null
                || order.getCustomer().getEmail().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "A customer email is required for Paystack card checkout");
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().signum() <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Order must have a positive KES total");
        }
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());
        String reference = "LF-ORD-" + UUID.randomUUID().toString().replace("-", "");
        CustomerOrderPaymentAttemptStore.StartResult start = attemptStore.start(
                order.getId(), reference, paystackPaymentService.mode(), order.getTotalAmount(),
                "KES", idempotencyKey);

        if (start.created()) {
            PaystackPaymentService.Initialization initialization;
            try {
                initialization = paystackPaymentService.initialize(
                        reference, order.getTotalAmount(), "KES", order.getCustomer().getEmail(),
                        "card", start.paymentId().toString(), order.getOrderNumber());
            } catch (PaystackPaymentService.ProviderOutcomeUnknownException unknown) {
                attemptStore.markOutcomeUnknown(start.attemptId(),
                        "Paystack initialization outcome is unknown; verification is required");
                return response(start.attemptId(), start.paymentId(),
                        "Payment status is being verified. Do not submit another payment yet.");
            } catch (ApiException rejection) {
                attemptStore.markFailed(start.attemptId(), rejection.getMessage());
                throw rejection;
            } catch (RuntimeException failure) {
                attemptStore.markFailed(start.attemptId(), "Paystack card checkout initialization failed");
                throw failure;
            }
            try {
                attemptStore.markAccepted(start.attemptId(), initialization);
            } catch (RuntimeException persistenceFailure) {
                attemptStore.markOutcomeUnknown(start.attemptId(),
                        "Paystack accepted initialization but local acceptance details could not be saved");
                throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                        "Paystack accepted the request, but its checkout link could not be saved; payment verification is pending");
            }
        }

        return response(start.attemptId(), start.paymentId(), start.created()
                ? "Continue to Paystack to complete your card payment"
                : "An existing Paystack payment attempt is being used; check its status before retrying");
    }

    /** Returns false when the reference belongs to the marketplace checkout handler instead. */
    @Transactional
    public boolean processWebhook(PaystackPaymentService.Webhook webhook) {
        if (webhook == null || webhook.reference() == null
                || !("charge.success".equals(webhook.event()) || "charge.failed".equals(webhook.event()))) {
            return false;
        }
        CustomerPaymentAttempt candidate = attemptRepository.findByProviderReference(webhook.reference()).orElse(null);
        if (candidate == null) return false;
        Order order = orderRepository.findByIdWithOwnershipForUpdate(candidate.getPayment().getOrder().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        CustomerPaymentAttempt attempt = attemptRepository.findByProviderReferenceForUpdate(webhook.reference())
                .orElse(null);
        if (attempt == null) return false;
        Payment payment = paymentRepository.findByIdForUpdate(attempt.getPayment().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        if (attempt.getStatus() == CustomerPaymentAttemptStatus.SUCCEEDED
                || attempt.getStatus() == CustomerPaymentAttemptStatus.FAILED
                || attempt.getStatus() == CustomerPaymentAttemptStatus.EXPIRED) return true;

        if ("charge.success".equals(webhook.event())) {
            verifyAmountAndCurrency(attempt, webhook.amountMinor(), webhook.currency());
            settle(order, payment, attempt);
        } else {
            fail(payment, attempt, webhook.gatewayResponse() == null
                    ? "Paystack reported that the card payment failed" : webhook.gatewayResponse());
        }
        return true;
    }

    @Transactional
    public boolean applyVerification(UUID attemptId, PaystackPaymentService.Verification verification) {
        CustomerPaymentAttempt candidate = attemptRepository.findById(attemptId).orElse(null);
        if (candidate == null) return true;
        Order order = orderRepository.findByIdWithOwnershipForUpdate(candidate.getPayment().getOrder().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        CustomerPaymentAttempt attempt = attemptRepository.findByProviderReferenceForUpdate(
                candidate.getProviderReference()).orElse(null);
        if (attempt == null || !active(attempt.getStatus())) return true;
        Payment payment = paymentRepository.findByIdForUpdate(attempt.getPayment().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        if (!attempt.getProviderReference().equals(verification.reference())) {
            throw new PaystackPaymentService.ProviderOutcomeUnknownException(
                    "Paystack verification reference did not match the payment attempt", null);
        }
        switch (verification.status().toLowerCase(Locale.ROOT)) {
            case "success" -> {
                verifyAmountAndCurrency(attempt, verification.amountMinor(), verification.currency());
                settle(order, payment, attempt);
                return true;
            }
            case "failed", "abandoned", "reversed" -> {
                fail(payment, attempt, "Paystack verified that the card payment did not complete");
                return true;
            }
            case "not_found" -> {
                if (attempt.getCreatedAt().isBefore(OffsetDateTime.now()
                        .minusMinutes(UNRESOLVED_REFERENCE_GRACE_MINUTES))) {
                    attempt.setStatus(CustomerPaymentAttemptStatus.EXPIRED);
                    attempt.setFailureReason("Paystack verification found no transaction for this reference");
                    attempt.setCompletedAt(OffsetDateTime.now());
                    attempt.setNextVerificationAt(null);
                    if (attempt.getProviderReference().equals(payment.getProviderReference())) {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setFailureReason(attempt.getFailureReason());
                        payment.setProviderAuthorizationUrl(null);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    public void reconcileDueAttempts() {
        for (UUID attemptId : attemptRepository.findDueForVerification(
                OffsetDateTime.now(), PageRequest.of(0, RECONCILIATION_BATCH_SIZE))) {
            try {
                CustomerPaymentAttempt attempt = attemptRepository.findById(attemptId).orElse(null);
                if (attempt == null || !active(attempt.getStatus())) continue;
                PaystackPaymentService.Verification verification =
                        paystackPaymentService.verifyTransaction(attempt.getProviderReference());
                Boolean applied = new TransactionTemplate(transactionManager)
                        .execute(status -> applyVerification(attemptId, verification));
                if (!Boolean.TRUE.equals(applied)) {
                    attemptStore.scheduleVerificationRetry(attemptId,
                            "Paystack has not reported a final card payment status");
                }
            } catch (RuntimeException failure) {
                log.warn("Could not reconcile Paystack customer-order payment attempt {}", attemptId, failure);
                try {
                    attemptStore.scheduleVerificationRetry(attemptId,
                            "Paystack verification failed; retry is scheduled");
                } catch (RuntimeException stateFailure) {
                    log.error("Could not schedule retry for customer-order payment attempt {}", attemptId,
                            stateFailure);
                }
            }
        }
    }

    public boolean hasUnresolvedAttemptForPayment(UUID paymentId) {
        return attemptRepository.existsByPayment_IdAndStatusIn(paymentId, java.util.List.of(
                CustomerPaymentAttemptStatus.INITIALIZING,
                CustomerPaymentAttemptStatus.PENDING,
                CustomerPaymentAttemptStatus.OUTCOME_UNKNOWN,
                CustomerPaymentAttemptStatus.REVIEW_REQUIRED));
    }

    private void settle(Order order, Payment payment, CustomerPaymentAttempt attempt) {
        if (payment.getStatus() == PaymentStatus.CONFIRMED || payment.getStatus() == PaymentStatus.RECONCILED) {
            if (!attempt.getProviderReference().equals(payment.getProviderReference())) {
                log.error("A second Paystack payment succeeded for already-paid customer order {}",
                        order.getOrderNumber());
                attempt.setStatus(CustomerPaymentAttemptStatus.SUCCEEDED);
                attempt.setCompletedAt(OffsetDateTime.now());
                attempt.setNextVerificationAt(null);
            }
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Verified payment cannot be applied because the order is no longer pending");
        }
        attempt.setStatus(CustomerPaymentAttemptStatus.SUCCEEDED);
        attempt.setFailureReason(null);
        attempt.setCompletedAt(OffsetDateTime.now());
        attempt.setLastVerifiedAt(OffsetDateTime.now());
        attempt.setNextVerificationAt(null);
        payment.setProvider(PaymentProvider.PAYSTACK);
        payment.setMethod(PaymentMethod.CARD);
        payment.setProviderReference(attempt.getProviderReference());
        payment.setProviderMode(attempt.getProviderMode());
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setFailureReason(null);
        payment.setConfirmedAt(OffsetDateTime.now());
        paymentRepository.saveAndFlush(payment);
        attemptRepository.saveAndFlush(attempt);
        orderService.confirmPaidOrder(order.getId());
        financialLedgerService.recordCustomerOrderCapture(payment.getId(), order.getOrganization(),
                payment.getAmount(), payment.getCurrency());
    }

    private void fail(Payment payment, CustomerPaymentAttempt attempt, String reason) {
        attempt.setStatus(CustomerPaymentAttemptStatus.FAILED);
        attempt.setFailureReason(reason);
        attempt.setCompletedAt(OffsetDateTime.now());
        attempt.setNextVerificationAt(null);
        if (attempt.getProviderReference().equals(payment.getProviderReference())
                && payment.getStatus() != PaymentStatus.CONFIRMED
                && payment.getStatus() != PaymentStatus.RECONCILED) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(reason);
            payment.setProviderAuthorizationUrl(null);
            paymentRepository.saveAndFlush(payment);
        }
        attemptRepository.saveAndFlush(attempt);
    }

    private static void verifyAmountAndCurrency(CustomerPaymentAttempt attempt, Long amountMinor, String currency) {
        if (amountMinor == null || currency == null || !attempt.getCurrency().equalsIgnoreCase(currency)
                || toMinorAmount(attempt.getAmount()).compareTo(BigDecimal.valueOf(amountMinor)) != 0) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Paystack payment amount or currency does not match the order");
        }
    }

    private PaymentInitiateResponse response(UUID attemptId, UUID paymentId, String message) {
        CustomerPaymentAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payment attempt not found"));
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        String customerMessage = attempt.getStatus() == CustomerPaymentAttemptStatus.OUTCOME_UNKNOWN
                || attempt.getStatus() == CustomerPaymentAttemptStatus.INITIALIZING
                ? "Payment status is being verified. Do not submit another payment yet."
                : attempt.getStatus() == CustomerPaymentAttemptStatus.REVIEW_REQUIRED
                ? "Payment needs manual review. Do not retry or choose another payment method yet."
                : message;
        return new PaymentInitiateResponse(payment.getId(), payment.getMpesaCheckoutRequestId(),
                payment.getStatus(), customerMessage, attempt.getProviderReference(), "PAYSTACK",
                attempt.getStatus() == CustomerPaymentAttemptStatus.REVIEW_REQUIRED
                        ? null : attempt.getAuthorizationUrl(), attempt.getProviderMode());
    }

    private static BigDecimal toMinorAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2);
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) return null;
        String normalized = key.trim();
        if (normalized.length() > 100) throw new ApiException(ErrorCode.BAD_REQUEST, "Idempotency key is too long");
        return normalized;
    }

    private static boolean active(CustomerPaymentAttemptStatus status) {
        return status == CustomerPaymentAttemptStatus.INITIALIZING
                || status == CustomerPaymentAttemptStatus.PENDING
                || status == CustomerPaymentAttemptStatus.OUTCOME_UNKNOWN;
    }

    private static void requireOrderAccess(AuthenticatedUser actor, Order order) {
        if (actor == null || actor.role() == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        boolean customerOwns = actor.role() == UserRole.CUSTOMER
                && actor.userId().equals(order.getCustomer().getId());
        boolean organizationOwns = actor.organizationId() != null && order.getOrganization() != null
                && actor.organizationId().equals(order.getOrganization().getId());
        if (!customerOwns && !organizationOwns) throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
}
