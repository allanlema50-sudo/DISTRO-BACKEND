package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CustomerPaymentAttempt;
import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.CustomerPaymentAttemptStatus;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.repository.CustomerPaymentAttemptRepository;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerOrderPaymentAttemptStoreTest {
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CustomerPaymentAttemptRepository attemptRepository;

    private CustomerOrderPaymentAttemptStore store;
    private UUID orderId;
    private UUID paymentId;
    private UUID attemptId;
    private String reference;
    private Order order;

    @BeforeEach
    void setUp() {
        store = new CustomerOrderPaymentAttemptStore(orderRepository, paymentRepository, attemptRepository);
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        reference = "LF-ORD-" + UUID.randomUUID().toString().replace("-", "");
        order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.PENDING);
    }

    @Test
    void reusesAttemptWhilePaymentIsStillInitializing() {
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.PAYSTACK);
        payment.setStatus(PaymentStatus.INITIATING);
        CustomerPaymentAttempt current = new CustomerPaymentAttempt();
        current.setId(attemptId);
        current.setStatus(CustomerPaymentAttemptStatus.INITIALIZING);
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(attemptRepository.findFirstByPayment_IdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(current));

        var result = store.start(orderId, reference, "TEST", new BigDecimal("100.00"), "KES", null);

        assertThat(result.created()).isFalse();
        assertThat(result.paymentId()).isEqualTo(paymentId);
        assertThat(result.attemptId()).isEqualTo(attemptId);
        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verify(attemptRepository, never()).saveAndFlush(any(CustomerPaymentAttempt.class));
    }

    @Test
    void acceptedProviderInitializationDoesNotReviveFailedPayment() {
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.PAYSTACK);
        payment.setProviderReference(reference);
        payment.setStatus(PaymentStatus.FAILED);
        CustomerPaymentAttempt attempt = new CustomerPaymentAttempt();
        attempt.setId(attemptId);
        attempt.setPayment(payment);
        attempt.setProviderReference(reference);
        attempt.setStatus(CustomerPaymentAttemptStatus.INITIALIZING);
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(attemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        store.markAccepted(attemptId, new PaystackPaymentService.Initialization(reference,
                "https://checkout.paystack.test/session", "access", "card"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getProviderAuthorizationUrl()).isNull();
        assertThat(attempt.getStatus()).isEqualTo(CustomerPaymentAttemptStatus.REVIEW_REQUIRED);
        verify(paymentRepository).saveAndFlush(payment);
        verify(attemptRepository).saveAndFlush(attempt);
    }

    @Test
    void oldUnresolvedAttemptMovesToManualReviewInsteadOfReleasingItsOrder() {
        ReflectionTestUtils.setField(store, "reviewAfterHours", 24L);
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.PAYSTACK);
        payment.setProviderReference(reference);
        payment.setStatus(PaymentStatus.PENDING);
        CustomerPaymentAttempt attempt = new CustomerPaymentAttempt();
        attempt.setId(attemptId);
        attempt.setPayment(payment);
        attempt.setProviderReference(reference);
        attempt.setStatus(CustomerPaymentAttemptStatus.OUTCOME_UNKNOWN);
        attempt.setCreatedAt(java.time.OffsetDateTime.now().minusHours(25));
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(attemptRepository.findByIdForUpdate(attemptId)).thenReturn(Optional.of(attempt));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        store.scheduleVerificationRetry(attemptId, "verification unavailable");

        assertThat(attempt.getStatus()).isEqualTo(CustomerPaymentAttemptStatus.REVIEW_REQUIRED);
        assertThat(attempt.getNextVerificationAt()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getFailureReason()).contains("manual review");
        verify(paymentRepository).saveAndFlush(payment);
        verify(attemptRepository).saveAndFlush(attempt);
    }
}
