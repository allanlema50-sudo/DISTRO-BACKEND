package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    // Placeholder until real distance/route-based pricing exists once Trips is built
    @Value("${app.orders.delivery-fee:0.00}")
    private BigDecimal deliveryFeeAmount;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.AWAITING_DISPATCH, OrderStatus.CANCELLED),
            OrderStatus.AWAITING_DISPATCH, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED),
            OrderStatus.IN_TRANSIT, Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED, OrderStatus.FAILED),
            OrderStatus.DELIVERED, Set.of(OrderStatus.REFUNDED),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.FAILED, Set.of(),
            OrderStatus.REFUNDED, Set.of()
    );

    @Transactional
    public OrderResponse createOrder(UUID customerId, OrderRequest request) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));

        Organization org = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrganization(org);
        order.setDeliveryAddress(request.deliveryAddress());
        order.setDeliveryLat(request.deliveryLat());
        order.setDeliveryLng(request.deliveryLng());

        Long seq = orderRepository.getNextOrderSequence();
        order.setOrderNumber(String.format("ORD-%d", seq));

        BigDecimal subtotal = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.items()) {
            StockItem stockItem = stockItemRepository.findById(itemReq.stockItemId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Stock item not found: " + itemReq.stockItemId()));

            if (stockItem.getOrganization() == null
                    || !org.getId().equals(stockItem.getOrganization().getId())) {
                throw new ApiException(ErrorCode.ACCESS_DENIED,
                        "Stock item does not belong to the selected organization");
            }

            if (!stockItem.isActive()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Stock item is not active: " + stockItem.getName());
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setStockItem(stockItem);
            orderItem.setQuantity(itemReq.quantity());
            orderItem.setUnitPrice(stockItem.getUnitPrice());

            BigDecimal lineTotal = stockItem.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            subtotal = subtotal.add(lineTotal);

            order.addOrderItem(orderItem);
        }

        order.setSubtotalAmount(subtotal);
        order.setDeliveryFee(deliveryFeeAmount);
        order.setTotalAmount(subtotal.add(deliveryFeeAmount));
        order.setStatus(OrderStatus.PENDING);
        order.setPlacedAt(java.time.OffsetDateTime.now());

        Order saved = orderRepository.save(order);
        return mapToResponse(saved);
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, UUID changedById, OrderStatusUpdate update) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));

        Set<OrderStatus> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Collections.emptySet());
        if (update.status() == OrderStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Orders are confirmed only after a verified payment");
        }
        if (!allowedNext.contains(update.status())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot transition from " + order.getStatus() + " to " + update.status());
        }

        User changedBy = userRepository.findById(changedById)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        OrderStatusHistory history = new OrderStatusHistory();
        history.setFromStatus(order.getStatus());
        history.setToStatus(update.status());
        history.setChangedBy(changedBy);
        history.setNote(update.note());

        order.addStatusHistory(history);
        order.setStatus(update.status());

        if (update.status() == OrderStatus.CONFIRMED) order.setConfirmedAt(java.time.OffsetDateTime.now());
        if (update.status() == OrderStatus.DELIVERED) order.setDeliveredAt(java.time.OffsetDateTime.now());
        if (update.status() == OrderStatus.CANCELLED) order.setCancelledAt(java.time.OffsetDateTime.now());

        if (update.status() == OrderStatus.CANCELLED || update.status() == OrderStatus.FAILED) {
            order.setCancellationReason(update.note());
        }

        Order saved = orderRepository.save(order);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        return mapToResponse(order);
    }

    @Transactional
    public Order confirmPaidOrder(UUID orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only pending orders can be confirmed by payment");
        }
        OrderStatusHistory history = new OrderStatusHistory();
        history.setFromStatus(OrderStatus.PENDING);
        history.setToStatus(OrderStatus.CONFIRMED);
        history.setNote("Confirmed after verified payment");
        order.addStatusHistory(history);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(java.time.OffsetDateTime.now());
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public User getCustomer(UUID orderId) {
        return orderRepository.findByIdWithOwnership(orderId)
                .map(Order::getCustomer)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersForCustomer(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerIdWithDetails(customerId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersForOrganization(UUID organizationId, Pageable pageable) {
        return orderRepository.findByOrganizationIdWithDetails(organizationId, pageable)
                .map(this::mapToResponse);
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream().map(i -> new OrderItemResponse(
                i.getId(),
                i.getStockItem().getId(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getLineTotal() != null ? i.getLineTotal() : i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))
        )).collect(Collectors.toList());

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomer().getId(),
                order.getOrganization().getId(),
                order.getStatus(),
                order.getDeliveryAddress(),
                order.getDeliveryLat(),
                order.getDeliveryLng(),
                order.getSubtotalAmount(),
                order.getDeliveryFee(),
                order.getTotalAmount(),
                order.getCancellationReason(),
                order.getPlacedAt(),
                order.getConfirmedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                items
        );
    }
}
