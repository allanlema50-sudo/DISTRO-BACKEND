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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
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
        ReflectionTestUtils.setField(paymentService, "verifyCallback", false);
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    @Test
    void customerCannotInitiatePaymentForAnotherCustomerOrder() {
        Order order = order();
        when(orderRepository.findByIdWithOwnership(orderId)).thenReturn(Optional.of(order));

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
        when(paymentRepository.findByMpesaCheckoutRequestId("ws_CO_123")).thenReturn(Optional.of(payment));

        MpesaCallbackRequest callback = new MpesaCallbackRequest();
        MpesaCallbackRequest.Body body = new MpesaCallbackRequest.Body();
        MpesaCallbackRequest.StkCallback stk = new MpesaCallbackRequest.StkCallback();
        stk.setCheckoutRequestId("ws_CO_123");
        stk.setResultCode(0);
        body.setStkCallback(stk);
        callback.setBody(body);

        assertThatThrownBy(() -> paymentService.processMpesaCallback(callback))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("receipt");
        verify(paymentRepository, never()).save(any());
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
