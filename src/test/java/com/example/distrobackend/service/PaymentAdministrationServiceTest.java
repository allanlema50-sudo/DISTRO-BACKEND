package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.entity.CustomerPaymentAttempt;
import com.example.distrobackend.Domain.enums.CustomerPaymentAttemptStatus;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.PaymentStatusUpdateRequest;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.CustomerPaymentAttemptRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAdministrationServiceTest {
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerPaymentAttemptRepository customerPaymentAttemptRepository;
    @Mock private OrderService orderService;
    @Mock private FinancialLedgerService financialLedgerService;
    @InjectMocks private PaymentAdministrationService service;

    @Test
    void administratorCannotManuallyFailAnUnresolvedPaystackPayment() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.PAYSTACK);
        payment.setProviderReference("LF-ORD-reference");
        Order order = new Order();
        order.setId(orderId);
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN,
                null, null, Instant.now().plusSeconds(3600));
        when(paymentRepository.findOrderIdByPaymentId(paymentId)).thenReturn(Optional.of(orderId));
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdWithOwnershipForUpdate(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.updateStatus(admin, paymentId,
                new PaymentStatusUpdateRequest(PaymentStatus.FAILED, "operator review")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must be verified");

        verify(paymentRepository, never()).save(any(Payment.class));
        verifyNoInteractions(orderService);
    }

    @Test
    void adminCanReleaseReservationAfterPaystackOutcomeWasEscalatedAndChecked() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setOrder(new Order());
        payment.getOrder().setId(orderId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(PaymentProvider.PAYSTACK);
        CustomerPaymentAttempt attempt = new CustomerPaymentAttempt();
        attempt.setPayment(payment);
        attempt.setStatus(CustomerPaymentAttemptStatus.REVIEW_REQUIRED);
        Order lockedOrder = new Order();
        lockedOrder.setId(orderId);
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN,
                null, null, Instant.now().plusSeconds(3600));
        when(paymentRepository.findOrderIdByPaymentId(paymentId)).thenReturn(Optional.of(orderId));
        when(orderRepository.findByIdWithOwnershipForUpdate(orderId)).thenReturn(Optional.of(lockedOrder));
        when(paymentRepository.findByIdWithOwnershipForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(customerPaymentAttemptRepository.findFirstByPayment_IdOrderByAttemptNumberDesc(paymentId))
                .thenReturn(Optional.of(attempt));

        service.updateStatus(admin, paymentId,
                new PaymentStatusUpdateRequest(PaymentStatus.FAILED,
                        "Verified absent in Paystack dashboard; safe to release reservation"));

        org.assertj.core.api.Assertions.assertThat(attempt.getStatus()).isEqualTo(CustomerPaymentAttemptStatus.FAILED);
        verify(customerPaymentAttemptRepository).saveAndFlush(attempt);
        verify(orderService).failPaymentAndReleaseOrder(eq(orderId), contains("Verified absent"));
    }
}
