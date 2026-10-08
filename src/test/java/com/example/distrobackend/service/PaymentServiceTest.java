package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.PaymentInitiateRequest;
import com.example.distrobackend.dto.mpesa.MpesaCallbackRequest;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private RestTemplate restTemplate;

    private PaymentService paymentService;
    private UUID customerId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, orderRepository, orderService, restTemplate);
        ReflectionTestUtils.setField(paymentService, "baseUrl", "https://sandbox.example");
        ReflectionTestUtils.setField(paymentService, "consumerKey", "key");
        ReflectionTestUtils.setField(paymentService, "consumerSecret", "secret");
        ReflectionTestUtils.setField(paymentService, "shortcode", "174379");
        ReflectionTestUtils.setField(paymentService, "passkey", "passkey");
        ReflectionTestUtils.setField(paymentService, "callbackUrl", "https://example/callback");
        ReflectionTestUtils.setField(paymentService, "callbackSecret", "test-callback-secret");
        ReflectionTestUtils.setField(paymentService, "verifyCallback", true);
        ReflectionTestUtils.setField(paymentService, "finalFailureResultCodes", "1032");
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    void customerCannotInitiatePaymentForAnotherCustomerOrder() {
        Order order = order();
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.initiatePayment(
                principal(UUID.randomUUID()), new PaymentInitiateRequest(orderId, "0712345678", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("permission");
        verifyNoInteractions(paymentRepository, restTemplate);
    }

    @Test
    void successfulCallbackWithoutReceiptIsRejected() {
        Order order = order();
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("100"));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMpesaCheckoutRequestId("ws_CO_123");
        payment.setMpesaMerchantRequestId("merchant_123");
        when(paymentRepository.findPaymentAndOrderIdsByMpesaCheckoutRequestId("ws_CO_123"))
                .thenReturn(Optional.of(new Object[]{payment.getId(), orderId}));
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(any())).thenReturn(Optional.of(payment));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "token")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(Map.of("ResultCode", "0")));

        MpesaCallbackRequest callback = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setCheckoutRequestId("ws_CO_123");
        stk.setMerchantRequestId("merchant_123");
        stk.setResultCode(0);
        body.setStkCallback(stk);
        callback.setBody(body);

        assertThatThrownBy(() -> paymentService.processMpesaCallback("test-callback-secret", callback))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("receipt");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void callbackVerificationCannotBeDisabled() {
        ReflectionTestUtils.setField(paymentService, "verifyCallback", false);
        Order order = order();
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("100"));
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMpesaCheckoutRequestId("ws_CO_123");
        payment.setMpesaMerchantRequestId("merchant_123");
        when(paymentRepository.findPaymentAndOrderIdsByMpesaCheckoutRequestId("ws_CO_123"))
                .thenReturn(Optional.of(new Object[]{payment.getId(), orderId}));
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdForUpdate(any())).thenReturn(Optional.of(payment));

        MpesaCallbackRequest callback = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setCheckoutRequestId("ws_CO_123");
        stk.setMerchantRequestId("merchant_123");
        stk.setResultCode(0);
        body.setStkCallback(stk);
        callback.setBody(body);

        assertThatThrownBy(() -> paymentService.processMpesaCallback("test-callback-secret", callback))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("verification must remain enabled");
        verifyNoInteractions(restTemplate);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void callbackWithInvalidSecretIsRejectedBeforeDatabaseLookup() {
        assertThatThrownBy(() -> paymentService.processMpesaCallback(
                "wrong-secret", new MpesaCallbackRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid M-Pesa callback credentials");
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void paymentInitiationRequiresCallbackSecretConfiguration() {
        ReflectionTestUtils.setField(paymentService, "callbackSecret", "");
        Order order = order();
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.initiatePayment(
                principal(customerId), new PaymentInitiateRequest(orderId, "0712345678", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Payment service is not configured");
        verifyNoInteractions(paymentRepository, restTemplate);
    }

    @Test
    void transientStkInitiationFailureKeepsOrderPendingAndAllowsRetry() {
        Order order = order();
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));

        assertThatThrownBy(() -> paymentService.initiatePayment(
                principal(customerId), new PaymentInitiateRequest(orderId, "0712345678", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Payment provider unavailable");

        org.assertj.core.api.Assertions.assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderService, never()).failPaymentAndReleaseOrder(any(), anyString());
        verify(paymentRepository).save(payment);
    }

    @Test
    void expiredPendingPaymentIsReconciledWithDarajaBeforeOrderExpiry() {
        Order order = order();
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMpesaCheckoutRequestId("ws_CO_expired");
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "token")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "The service request is processed successfully")));

        org.assertj.core.api.Assertions.assertThat(paymentService.reconcileExpiredPayment(orderId)).isTrue();

        org.assertj.core.api.Assertions.assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RECONCILED);
        verify(orderService).confirmPaidOrder(orderId);
    }

    @Test
    void unknownNonzeroQueryResultRemainsPendingForRetry() {
        Order order = order();
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMpesaCheckoutRequestId("ws_CO_unknown");
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "token")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(Map.of("ResultCode", "1037", "ResultDesc", "Provider status is not final")));

        assertThatThrownBy(() -> paymentService.reconcileExpiredPayment(orderId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("status is not final");

        org.assertj.core.api.Assertions.assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verify(orderService, never()).failPaymentAndReleaseOrder(any(), anyString());
        verify(orderService, never()).confirmPaidOrder(any());
    }

    @Test
    void configuredTerminalQueryResultFailsPaymentAndReleasesReservation() {
        Order order = order();
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMpesaCheckoutRequestId("ws_CO_cancelled");
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(Map.of("access_token", "token")));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .thenReturn(ResponseEntity.ok(Map.of("ResultCode", "1032", "ResultDesc", "Request cancelled")));

        org.assertj.core.api.Assertions.assertThat(paymentService.reconcileExpiredPayment(orderId)).isTrue();

        org.assertj.core.api.Assertions.assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderService).failPaymentAndReleaseOrder(orderId, "Request cancelled");
    }

    private Order order() {
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        organization.setType(Organizationtype.DISTRIBUTOR);
        User customer = new User();
        customer.setId(customerId);
        customer.setRole(UserRole.CUSTOMER);
        Order order = new Order();
        order.setId(orderId);
        order.setCustomer(customer);
        order.setOrganization(organization);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("100"));
        return order;
    }

    private AuthenticatedUser principal(UUID userId) {
        return new AuthenticatedUser(userId, UserRole.CUSTOMER, null, null,
                Instant.now().plus(Duration.ofMinutes(5)));
    }
}
