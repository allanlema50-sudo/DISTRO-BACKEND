package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.OrderResponse;
import com.example.distrobackend.dto.OrderStatsResponse;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;

    // =========================================================
    // GET ORDERS
    // =========================================================

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(
            AuthenticatedUser me,
            int page,
            int size,
            String search
    ) {

        User user = userRepository.findById(me.userId())
                .orElseThrow(() ->
                        new IllegalStateException("Authenticated user not found"));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100)
        );

        String cleanSearch = search == null
                ? ""
                : search.trim();

        Page<Order> orders;

        // Platform admin can see all orders.
        if (user.getRole().isPlatformAdmin()) {

            if (cleanSearch.isBlank()) {
                orders = orderRepository.findAll(pageable);
            } else {
                orders = orderRepository.searchAllOrders(
                        cleanSearch,
                        pageable
                );
            }

        } else {

            // Organization users can only see their own organization's orders.
            if (user.getOrganization() == null) {
                throw new IllegalStateException(
                        "This user is not assigned to an organization"
                );
            }

            UUID organizationId =
                    user.getOrganization().getId();

            if (cleanSearch.isBlank()) {

                orders =
                        orderRepository.findByOrganizationIdWithDetails(
                                organizationId,
                                pageable
                        );

            } else {

                orders =
                        orderRepository.searchOrganizationOrders(
                                organizationId,
                                cleanSearch,
                                pageable
                        );
            }
        }

        return orders.map(this::toResponse);
    }

    // =========================================================
    // GET SINGLE ORDER
    // =========================================================

    @Transactional(readOnly = true)
    public OrderResponse getOrder(
            UUID orderId,
            AuthenticatedUser me
    ) {

        User user = userRepository.findById(me.userId())
                .orElseThrow(() ->
                        new IllegalStateException("Authenticated user not found"));

        Order order =
                orderRepository.findByIdWithItems(orderId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Order not found"
                                ));

        // Platform admin can view any order.
        if (user.getRole().isPlatformAdmin()) {
            return toResponse(order);
        }

        // Organization users can only view their own organization's orders.
        if (user.getOrganization() == null) {
            throw new IllegalStateException(
                    "This user is not assigned to an organization"
            );
        }

        UUID organizationId =
                user.getOrganization().getId();

        if (order.getOrganization() == null
                || !organizationId.equals(
                        order.getOrganization().getId()
                )) {

            throw new IllegalArgumentException(
                    "You do not have access to this order"
            );
        }

        return toResponse(order);
    }

    // =========================================================
    // ORDER STATS
    // =========================================================

    @Transactional
    public Order confirmPaidOrder(UUID orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        "Order not found"
                ));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(
                    ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only pending orders can be confirmed by payment"
            );
        }

        com.example.distrobackend.Domain.entity.OrderStatusHistory history =
                new com.example.distrobackend.Domain.entity.OrderStatusHistory();

        history.setFromStatus(OrderStatus.PENDING);
        history.setToStatus(OrderStatus.CONFIRMED);
        history.setNote("Confirmed after verified payment");

        order.addStatusHistory(history);
        order.setStatus(OrderStatus.CONFIRMED);

        return orderRepository.save(order);
    }
    public OrderStatsResponse getStats(
            AuthenticatedUser me
    ) {

        User user = userRepository.findById(me.userId())
                .orElseThrow(() ->
                        new IllegalStateException("Authenticated user not found"));

        long totalOrders;
        long activeOrders;
        long pendingOrders;
        BigDecimal totalOrderValue;

        List<OrderStatus> inactiveStatuses = List.of(
                OrderStatus.DELIVERED,
                OrderStatus.CANCELLED,
                OrderStatus.REFUNDED,
                OrderStatus.FAILED
        );

        // =====================================================
        // PLATFORM ADMIN
        // =====================================================

        if (user.getRole().isPlatformAdmin()) {

            totalOrders =
                    orderRepository.count();

            pendingOrders =
                    orderRepository.countByStatus(
                            OrderStatus.PENDING
                    );

            activeOrders =
                    orderRepository.countByStatusNotIn(
                            inactiveStatuses
                    );

            totalOrderValue =
                    orderRepository.sumTotalAmount();

        }

        // =====================================================
        // ORGANIZATION USER
        // =====================================================

        else {

            if (user.getOrganization() == null) {
                throw new IllegalStateException(
                        "This user is not assigned to an organization"
                );
            }

            UUID organizationId =
                    user.getOrganization().getId();

            totalOrders =
                    orderRepository.countByOrganizationId(
                            organizationId
                    );

            pendingOrders =
                    orderRepository.countByOrganizationIdAndStatus(
                            organizationId,
                            OrderStatus.PENDING
                    );

            activeOrders =
                    orderRepository.countByOrganizationIdAndStatusNotIn(
                            organizationId,
                            inactiveStatuses
                    );

            totalOrderValue =
                    orderRepository.sumTotalAmountByOrganizationId(
                            organizationId
                    );
        }

        if (totalOrderValue == null) {
            totalOrderValue = BigDecimal.ZERO;
        }

        return new OrderStatsResponse(
                totalOrders,
                activeOrders,
                pendingOrders,
                totalOrderValue
        );
    }

    // =========================================================
    // MAP ENTITY TO RESPONSE
    // =========================================================

    private OrderResponse toResponse(Order order) {

        String customerName = null;
        String customerEmail = null;

        if (order.getCustomer() != null) {
            customerName =
                    order.getCustomer().getFullName();

            customerEmail =
                    order.getCustomer().getEmail();
        }

        UUID organizationId = null;
        String organizationName = null;

        if (order.getOrganization() != null) {
            organizationId =
                    order.getOrganization().getId();

            organizationName =
                    order.getOrganization().getName();
        }

        int itemCount =
                order.getOrderItems() == null
                        ? 0
                        : order.getOrderItems().stream()
                                .mapToInt(item -> item.getQuantity())
                                .sum();

        String frontendOrderStatus =
                mapOrderStatus(order.getStatus());

        String paymentStatus =
                getPaymentStatus(order.getId());

        String deliveryStatus =
                getDeliveryStatus(order.getId());

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                customerName,
                customerEmail,
                organizationId,
                organizationName,
                order.getTotalAmount(),
                itemCount,
                frontendOrderStatus,
                paymentStatus,
                deliveryStatus,
                order.getDeliveryAddress(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    // =========================================================
    // ORDER STATUS MAPPING
    // =========================================================

    private String mapOrderStatus(OrderStatus status) {

        if (status == null) {
            return "PENDING";
        }

        return switch (status) {

            case PENDING ->
                    "PENDING";

            case CONFIRMED ->
                    "CONFIRMED";

            case PROCESSING ->
                    "PROCESSING";

            case AWAITING_DISPATCH ->
                    "PROCESSING";

            case IN_TRANSIT ->
                    "SHIPPED";

            case DELIVERED ->
                    "DELIVERED";

            case CANCELLED ->
                    "CANCELLED";

            case REFUNDED ->
                    "CANCELLED";

            case FAILED ->
                    "CANCELLED";
        };
    }

    // =========================================================
    // PAYMENT STATUS
    // =========================================================

    private String getPaymentStatus(UUID orderId) {

        return paymentRepository
                .findByOrderId(orderId)
                .map(payment -> mapPaymentStatus(payment.getStatus()))
                .orElse("PENDING");
    }

    private String mapPaymentStatus(
            PaymentStatus status
    ) {

        if (status == null) {
            return "PENDING";
        }

        return switch (status) {

            case INITIATING ->
                    "PENDING";

            case PENDING ->
                    "PENDING";

            case CONFIRMED ->
                    "PAID";

            case FAILED ->
                    "FAILED";

            case REVERSED ->
                    "REFUNDED";

            case RECONCILED ->
                    "PAID";
        };
    }

    // =========================================================
    // DELIVERY STATUS
    // =========================================================

    private String getDeliveryStatus(UUID orderId) {

        return tripRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .map(trip -> mapTripStatus(trip.getStatus()))
                .orElse("PENDING");
    }

    private String mapTripStatus(
            TripStatus status
    ) {

        if (status == null) {
            return "PENDING";
        }

        return switch (status) {

            case UNASSIGNED ->
                    "PENDING";

            case ASSIGNED ->
                    "ASSIGNED";

            case IN_PROGRESS ->
                    "IN_TRANSIT";

            case COMPLETED ->
                    "DELIVERED";

            case CANCELLED ->
                    "FAILED";

            case FAILED ->
                    "FAILED";
        };
    }
}
