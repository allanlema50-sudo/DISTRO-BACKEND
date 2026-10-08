package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.InsufficientStockException;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final StockItemRepository stockItemRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationNotificationService notificationService;

    // Placeholder until real distance/route-based pricing exists once Trips is built
    @Value("${app.orders.delivery-fee:0.00}")
    private BigDecimal deliveryFeeAmount;

    @Value("${app.orders.reservation-ttl:15m}")
    private Duration reservationTtl;

    @Value("${app.orders.max-open-pending-per-customer:5}")
    private int maxOpenPendingOrdersPerCustomer;

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

        if (maxOpenPendingOrdersPerCustomer > 0
                && orderRepository.countByCustomer_IdAndStatus(customerId, OrderStatus.PENDING)
                >= maxOpenPendingOrdersPerCustomer) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "You have reached the maximum number of unpaid orders. Complete or cancel an existing order first");
        }

        Organization org = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
        if (org.getType() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Customers can only order from a distributor offer catalog");
        }

        Order order = new Order();
        order.setCustomer(customer);
        order.setOrganization(org);
        order.setDeliveryAddress(request.deliveryAddress());
        order.setDeliveryLat(request.deliveryLat());
        order.setDeliveryLng(request.deliveryLng());

        Long seq = orderRepository.getNextOrderSequence();
        order.setOrderNumber(String.format("ORD-%d", seq));

        BigDecimal subtotal = BigDecimal.ZERO;

        Set<UUID> requestedItemIds = new HashSet<>();
        List<OrderItemRequest> sortedItems = request.items().stream()
                .sorted(java.util.Comparator.comparing(OrderItemRequest::stockItemId))
                .toList();

        for (OrderItemRequest itemReq : sortedItems) {
            if (!requestedItemIds.add(itemReq.stockItemId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Each stock item may appear only once in an order");
            }

            StockItem stockItem = stockItemRepository.findByIdForUpdate(itemReq.stockItemId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Stock item not found: " + itemReq.stockItemId()));

            if (stockItem.getOrganization() == null
                    || !org.getId().equals(stockItem.getOrganization().getId())) {
                throw new ApiException(ErrorCode.ACCESS_DENIED,
                        "Stock item does not belong to the selected organization");
            }

            if (!stockItem.isActive()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Stock item is not active: " + stockItem.getName());
            }

            if (stockItem.getSourceStockItem() == null
                    || stockItem.getSourceStockItem().getOrganization() == null
                    || stockItem.getSourceStockItem().getOrganization().getType() != Organizationtype.MANUFACTURER
                    || stockItem.getWarehouse() == null
                    || !stockItem.getWarehouse().isActive()
                    || !org.getId().equals(stockItem.getWarehouse().getOrganization().getId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "The selected stock item is not an active distributor offer");
            }

            int available = stockItem.getAvailableQuantity();
            if (itemReq.quantity() > available) {
                notificationService.notify(
                        org,
                        "ORDER_STOCK_REJECTED",
                        "Order rejected: insufficient stock",
                        "Customer " + customer.getId() + " requested " + itemReq.quantity()
                                + " units of " + stockItem.getName() + " (" + stockItem.getSku()
                                + "), but only " + available + " units were available.",
                        "CUSTOMER_ORDER_ATTEMPT",
                        null);
                throw new InsufficientStockException(
                        "Insufficient available stock for " + stockItem.getSku()
                                + ": requested " + itemReq.quantity() + ", available " + available);
            }

            try {
                stockItem.setReservedQuantity(Math.addExact(
                        stockItem.getReservedQuantity(), itemReq.quantity()));
            } catch (ArithmeticException ex) {
                throw new InsufficientStockException("Requested stock reservation is too large");
            }

            OrderItem orderItem = new OrderItem();
            orderItem.setStockItem(stockItem);
            orderItem.setQuantity(itemReq.quantity());
            orderItem.setUnitPrice(stockItem.getUnitPrice());
            orderItem.setStockCheckStatus("RESERVED");

            BigDecimal lineTotal = stockItem.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            subtotal = subtotal.add(lineTotal);

            order.addOrderItem(orderItem);
        }

        order.setSubtotalAmount(subtotal);
        order.setDeliveryFee(deliveryFeeAmount);
        order.setTotalAmount(subtotal.add(deliveryFeeAmount));
        order.setStatus(OrderStatus.PENDING);
        OffsetDateTime now = OffsetDateTime.now();
        order.setPlacedAt(now);
        order.setReservationExpiresAt(now.plus(reservationTtl));

        Order saved = orderRepository.save(order);
        return mapToResponse(saved);
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, UUID changedById, OrderStatusUpdate update) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
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

        if (update.status() == OrderStatus.CONFIRMED) {
            order.setConfirmedAt(OffsetDateTime.now());
            order.setReservationExpiresAt(null);
        }
        if (update.status() == OrderStatus.DELIVERED) order.setDeliveredAt(java.time.OffsetDateTime.now());
        if (update.status() == OrderStatus.CANCELLED) {
            order.setCancelledAt(java.time.OffsetDateTime.now());
            order.setReservationExpiresAt(null);
        }

        if (update.status() == OrderStatus.CANCELLED || update.status() == OrderStatus.FAILED) {
            order.setCancellationReason(update.note());
            order.setReservationExpiresAt(null);
            releaseOrRestoreInventory(order);
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
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
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
        order.setConfirmedAt(OffsetDateTime.now());
        order.setReservationExpiresAt(null);
        commitReservations(order);
        return orderRepository.save(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failPaymentAndReleaseOrder(UUID orderId, String reason) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        releaseOrRestoreInventory(order);
        order.setStatus(OrderStatus.FAILED);
        order.setCancellationReason(reason);
        order.setReservationExpiresAt(null);
        OrderStatusHistory history = new OrderStatusHistory();
        history.setFromStatus(OrderStatus.PENDING);
        history.setToStatus(OrderStatus.FAILED);
        history.setNote(reason);
        order.addStatusHistory(history);
        orderRepository.save(order);
    }

    /** Releases an abandoned unpaid reservation after its payment window expires. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireReservationAndFailOrder(UUID orderId, OffsetDateTime now) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (order.getStatus() != OrderStatus.PENDING
                || order.getReservationExpiresAt() == null
                || order.getReservationExpiresAt().isAfter(now)) {
            return false;
        }

        releaseOrRestoreInventory(order);
        order.setStatus(OrderStatus.FAILED);
        order.setCancellationReason("Payment window expired");
        order.setReservationExpiresAt(null);
        OrderStatusHistory history = new OrderStatusHistory();
        history.setFromStatus(OrderStatus.PENDING);
        history.setToStatus(OrderStatus.FAILED);
        history.setNote("Payment window expired and reserved stock was released");
        order.addStatusHistory(history);
        orderRepository.save(order);
        return true;
    }

    private void commitReservations(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            StockItem stockItem = stockItemRepository.findByIdForUpdate(item.getStockItem().getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND,
                            "Reserved stock item no longer exists"));
            if ("RESERVED".equals(item.getStockCheckStatus())) {
                if (stockItem.getReservedQuantity() < item.getQuantity()
                        || stockItem.getQuantityOnHand() < item.getQuantity()) {
                    throw new InsufficientStockException(
                            "Reserved stock is no longer available for " + stockItem.getSku());
                }
                stockItem.setReservedQuantity(stockItem.getReservedQuantity() - item.getQuantity());
                stockItem.setQuantityOnHand(stockItem.getQuantityOnHand() - item.getQuantity());
                item.setStockCheckStatus("COMMITTED");
                stockItemRepository.save(stockItem);
            }
        }
    }

    private void releaseOrRestoreInventory(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            StockItem stockItem = stockItemRepository.findByIdForUpdate(item.getStockItem().getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND,
                            "Order stock item no longer exists"));
            if ("RESERVED".equals(item.getStockCheckStatus())) {
                if (stockItem.getReservedQuantity() < item.getQuantity()) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "Order reservation is inconsistent and cannot be released safely");
                }
                stockItem.setReservedQuantity(stockItem.getReservedQuantity() - item.getQuantity());
                item.setStockCheckStatus("RELEASED");
                stockItemRepository.save(stockItem);
            } else if ("COMMITTED".equals(item.getStockCheckStatus())) {
                try {
                    stockItem.setQuantityOnHand(Math.addExact(
                            stockItem.getQuantityOnHand(), item.getQuantity()));
                } catch (ArithmeticException ex) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "Stock quantity overflow while restoring cancelled order");
                }
                item.setStockCheckStatus("RETURNED");
                stockItemRepository.save(stockItem);
            }
        }
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
                order.getReservationExpiresAt(),
                order.getConfirmedAt(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                items
        );
    }
}
