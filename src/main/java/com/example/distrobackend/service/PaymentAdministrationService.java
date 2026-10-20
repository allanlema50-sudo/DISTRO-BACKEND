package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.PaymentResponse;
import com.example.distrobackend.dto.PaymentStatusUpdateRequest;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentAdministrationService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(AuthenticatedUser actor, Pageable pageable) {
        if (actor == null || actor.role() == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        return switch (actor.role()) {
            case SUPER_ADMIN -> paymentRepository.findAllWithOwnership(pageable).map(PaymentResponse::from);
            case CUSTOMER -> paymentRepository.findByOrder_Customer_Id(actor.userId(), pageable)
                    .map(PaymentResponse::from);
            case DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF -> paymentRepository
                    .findByOrder_Organization_Id(actor.organizationId(), pageable).map(PaymentResponse::from);
            default -> throw new ApiException(ErrorCode.ACCESS_DENIED);
        };
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(AuthenticatedUser actor, UUID paymentId) {
        Payment payment = paymentRepository.findByIdWithOwnership(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        requireAccess(actor, payment.getOrder());
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getForOrder(AuthenticatedUser actor, UUID orderId) {
        Payment payment = paymentRepository.findByOrderIdWithOwnership(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        requireAccess(actor, payment.getOrder());
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse updateStatus(AuthenticatedUser actor, UUID paymentId,
                                        PaymentStatusUpdateRequest request) {
        requirePlatformAdmin(actor);
        UUID orderId = paymentRepository.findOrderIdByPaymentId(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        orderRepository.findByIdWithOwnershipForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        Payment payment = paymentRepository.findByIdWithOwnershipForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        PaymentStatus requested = request.status();
        if (requested == PaymentStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Successful payments must be confirmed by verified M-Pesa callbacks");
        }
        if (payment.getStatus() == PaymentStatus.CONFIRMED
                || payment.getStatus() == PaymentStatus.RECONCILED
                || payment.getStatus() == PaymentStatus.REVERSED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "A terminal payment cannot be changed through this endpoint");
        }
        if (requested != PaymentStatus.FAILED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Manual payment updates may only mark an unsettled payment failed");
        }
        if (payment.getStatus() == PaymentStatus.PENDING
                && payment.getMpesaCheckoutRequestId() != null
                && !payment.getMpesaCheckoutRequestId().isBlank()) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "An accepted M-Pesa payment must be reconciled before it can be failed manually");
        }
        String note = request.note() == null || request.note().isBlank()
                ? "Payment status updated by platform administrator" : request.note().trim();
        payment.setStatus(requested);
        payment.setFailureReason(note);
        paymentRepository.save(payment);
        orderService.failPaymentAndReleaseOrder(payment.getOrder().getId(), note);
        return PaymentResponse.from(payment);
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static void requireAccess(AuthenticatedUser actor, Order order) {
        if (actor == null || actor.role() == null || order == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        boolean allowed = switch (actor.role()) {
            case SUPER_ADMIN -> true;
            case CUSTOMER -> order.getCustomer() != null && actor.userId().equals(order.getCustomer().getId());
            case DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF -> order.getOrganization() != null
                    && actor.organizationId() != null
                    && actor.organizationId().equals(order.getOrganization().getId());
            default -> false;
        };
        if (!allowed) throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
}
