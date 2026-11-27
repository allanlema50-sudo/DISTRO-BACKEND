package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CustomerPaymentAttempt;
import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.CustomerPaymentAttemptStatus;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.PaymentInitiateRequest;
import com.example.distrobackend.repository.CustomerPaymentAttemptRepository;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerOrderPaystackPaymentServiceTest {
    @Mock private PaystackPaymentService paystack;
    @Mock private CustomerOrderPaymentAttemptStore attemptStore;
    @Mock private CustomerPaymentAttemptRepository attemptRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private FinancialLedgerService financialLedgerService;
    @Mock private PlatformTransactionManager transactionManager;

    private CustomerOrderPaystackPaymentService service;
    private Order order;
    private UUID orderId;
    private UUID paymentId;
    private UUID attemptId;
    private String providerReference;

    @BeforeEach
    void setUp() {
        service = new CustomerOrderPaystackPaymentService(paystack, attemptStore, attemptRepository,
                paymentRepository, orderRepository, orderService, financialLedgerService, transactionManager);
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        attemptId = UUID.randomUUID();
        providerReference = "LF-ORD-" + UUID.randomUUID().toString().replace("-", "");
        User customer = new User();
        customer.setId(UUID.randomUUID());
        customer.setEmail("buyer@example.test");
        order = new Order();
        order.setId(orderId);
        order.setOrderNumber("ORD-1001");
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("1250.00"));
    }

    @Test
    void startsPaystackCardCheckoutAndReturnsHostedUrl() {
        AuthenticatedUser actor = customerActor(order.getCustomer().getId());
        when(orderRepository.findByIdWithOwnership(orderId)).thenReturn(Optional.of(order));
        when(paystack.mode()).thenReturn("TEST");
        when(attemptStore.start(eq(orderId), anyString(), eq("TEST"), eq(new BigDecimal("1250.00")),
                eq("KES"), eq("card-key")))
                .thenReturn(new CustomerOrderPaymentAttemptStore.StartResult(paymentId, attemptId, true));
        when(paystack.initialize(anyString(), any(), eq("KES"), eq("buyer@example.test"), eq("card"),
                eq(paymentId.toString()), eq("ORD-1001")))
                .thenReturn(new PaystackPaymentService.Initialization(providerReference,
                        "https://checkout.paystack.test/abc", "access-code", "card"));
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);
        CustomerPaymentAttempt attempt = new CustomerPaymentAttempt();
        attempt.setId(attemptId);
        attempt.setStatus(CustomerPaymentAttemptStatus.PENDING);
        attempt.setAuthorizationUrl("https://checkout.paystack.test/abc");
        attempt.setProviderMode("TEST");
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        var response = service.initiate(actor,
                new PaymentInitiateRequest(orderId, null, "card-key", "CUSTOMER_ORDER", "PAYSTACK"));

        assertThat(response.provider()).isEqualTo("PAYSTACK");
        assertThat(response.providerMode()).isEqualTo("TEST");
        assertThat(response.authorizationUrl()).isEqualTo("https://checkout.paystack.test/abc");
        verify(paystack).initialize(anyString(), any(), eq("KES"), eq("buyer@example.test"), eq("card"),
                eq(paymentId.toString()), eq("ORD-1001"));
        verify(attemptStore).markAccepted(eq(attemptId), any(PaystackPaymentService.Initialization.class));
        verify(attemptStore, never()).markFailed(any(), anyString());
    }

    @Test
    void acceptedInitializationWhoseLocalSaveFailsRemainsOutcomeUnknown() {
        AuthenticatedUser actor = customerActor(order.getCustomer().getId());
        when(orderRepository.findByIdWithOwnership(orderId)).thenReturn(Optional.of(order));
        when(paystack.mode()).thenReturn("TEST");
        when(attemptStore.start(eq(orderId), anyString(), eq("TEST"), eq(new BigDecimal("1250.00")),
                eq("KES"), isNull()))
                .thenReturn(new CustomerOrderPaymentAttemptStore.StartResult(paymentId, attemptId, true));
        when(paystack.initialize(anyString(), any(), eq("KES"), eq("buyer@example.test"), eq("card"),
                eq(paymentId.toString()), eq("ORD-1001")))
                .thenReturn(new PaystackPaymentService.Initialization(providerReference,
                        "https://checkout.paystack.test/abc", "access-code", "card"));
        doThrow(new IllegalStateException("database unavailable"))
                .when(attemptStore).markAccepted(eq(attemptId), any(PaystackPaymentService.Initialization.class));

        assertThatThrownBy(() -> service.initiate(actor,
                new PaymentInitiateRequest(orderId, null, null, "CUSTOMER_ORDER", "PAYSTACK")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("verification is pending");

        verify(attemptStore).markOutcomeUnknown(eq(attemptId), contains("Paystack accepted initialization"));
        verify(attemptStore, never()).markFailed(any(), anyString());
    }

    @Test
    void scheduledVerificationRunsLockedSettlementWorkInsideTransaction() {
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setOrder(order);
        payment.setProvider(PaymentProvider.PAYSTACK);
        payment.setProviderReference(providerReference);
        payment.setStatus(PaymentStatus.PENDING);
        CustomerPaymentAttempt attempt = new CustomerPaymentAttempt();
        attempt.setId(attemptId);
        attempt.setPayment(payment);
        attempt.setProviderReference(providerReference);
        attempt.setProviderMode("TEST");
        attempt.setStatus(CustomerPaymentAttemptStatus.PENDING);
        attempt.setAmount(new BigDecimal("1250.00"));
        attempt.setCurrency("KES");
        attempt.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        when(attemptRepository.findDueForVerification(any(), any())).thenReturn(new PageImpl<>(List.of(attemptId)));
        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(paystack.verifyTransaction(providerReference)).thenReturn(
                new PaystackPaymentService.Verification(providerReference, "pending", null, "KES"));
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(attemptRepository.findByProviderReferenceForUpdate(providerReference)).thenReturn(Optional.of(attempt));
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        service.reconcileDueAttempts();

        verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        verify(orderRepository).findByIdWithOwnershipForUpdate(orderId);
        verify(attemptRepository).findByProviderReferenceForUpdate(providerReference);
        verify(paymentRepository).findByIdForUpdate(paymentId);
        verify(attemptStore).scheduleVerificationRetry(eq(attemptId), contains("final card payment status"));
    }

    private static AuthenticatedUser customerActor(UUID customerId) {
        return new AuthenticatedUser(customerId, UserRole.CUSTOMER, null, null,
                Instant.now().plusSeconds(3600));
    }
}
