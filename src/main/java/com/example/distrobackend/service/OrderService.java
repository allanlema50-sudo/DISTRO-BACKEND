package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.InsufficientStockException;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.StockLocationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
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
    private final StockMovementRepository stockMovementRepository;
    private final StockLocationRepository stockLocationRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PaymentRepository paymentRepository;
    private final OrganizationNotificationService notificationService;
    private final PlatformSettingsService platformSettingsService;

    @Value("${app.orders.reservation-ttl:15m}")
    private Duration reservationTtl;

    @Value("${app.orders.max-open-pending-per-customer:5}")
    private int maxOpenPendingOrdersPerCustomer;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED),
            OrderStatus.CONFIRMED, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.READY_TO_PACK, OrderStatus.CANCELLED),
            OrderStatus.READY_TO_PACK, Set.of(OrderStatus.AWAITING_DISPATCH, OrderStatus.CANCELLED),
            OrderStatus.AWAITING_DISPATCH, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.CANCELLED),
            // Dispatched inventory cannot be restored by an order status update.
            // A return requires a separate warehouse/driver custody workflow.
            OrderStatus.IN_TRANSIT, Set.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, Set.of(OrderStatus.REFUNDED),
            OrderStatus.CANCELLED, Set.of(),
            OrderStatus.FAILED, Set.of(),
            OrderStatus.REFUNDED, Set.of()
    );

    @Transactional
    public OrderResponse createOrder(UUID customerId, OrderRequest request) {
        User customer = userRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));

        if (maxOpenPendingOrdersPerCustomer > 0
                && orderRepository.countByCustomer_IdAndStatus(customerId, OrderStatus.PENDING)
                >= maxOpenPendingOrdersPerCustomer) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "You have reached the maximum number of unpaid orders. Complete or cancel an existing order first");
        }

        Set<UUID> requestedItemIds = new HashSet<>();
        List<OrderItemRequest> sortedItems = request.items().stream()
                .sorted(java.util.Comparator.comparing(OrderItemRequest::stockItemId))
                .toList();

        Map<UUID, StockItem> resolvedStockItems = new HashMap<>();
        for (OrderItemRequest itemReq : sortedItems) {
            if (!requestedItemIds.add(itemReq.stockItemId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Each stock item may appear only once in an order");
            }

            StockItem stockItem = stockItemRepository.findByIdForUpdate(itemReq.stockItemId()).orElse(null);
            if (stockItem == null) {
                // If not found by direct ID, check if the ID is a manufacturer source product with an offer
                List<StockItem> offers = stockItemRepository.findOffersBySourceStockItemIdForUpdate(itemReq.stockItemId());
                if (!offers.isEmpty()) {
                    stockItem = offers.stream()
                            .filter(o -> request.organizationId() == null || (o.getOrganization() != null && request.organizationId().equals(o.getOrganization().getId())))
                            .findFirst()
                            .orElse(offers.get(0));
                }
            }
            if (stockItem == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Stock item not found: " + itemReq.stockItemId());
            }
            resolvedStockItems.put(itemReq.stockItemId(), stockItem);
        }

        Organization org = null;
        if (request.organizationId() != null) {
            org = organizationRepository.findById(request.organizationId()).orElse(null);
        }

        // If organization was not provided, not found, or is not a distributor, resolve from the items
        if (org == null || org.getType() != Organizationtype.DISTRIBUTOR) {
            StockItem firstItem = resolvedStockItems.get(sortedItems.get(0).stockItemId());
            if (firstItem.getOrganization() != null && firstItem.getOrganization().getType() == Organizationtype.DISTRIBUTOR) {
                org = firstItem.getOrganization();
            } else if (org == null) {
                throw new ApiException(ErrorCode.NOT_FOUND, "Organization not found");
            } else {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Customers can only order from a distributor offer catalog");
            }
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

        for (OrderItemRequest itemReq : sortedItems) {
            StockItem stockItem = resolvedStockItems.get(itemReq.stockItemId());

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
        BigDecimal effectiveDeliveryFee = platformSettingsService.resolveDeliveryFee();
        order.setDeliveryFee(effectiveDeliveryFee);
        order.setTotalAmount(subtotal.add(effectiveDeliveryFee));
        order.setStatus(OrderStatus.PENDING);
        OffsetDateTime now = OffsetDateTime.now();
        order.setPlacedAt(now);
        order.setReservationExpiresAt(now.plus(reservationTtl));

        Order saved = orderRepository.save(order);
        return mapToResponse(saved);
    }

    @Transactional
    public OrderResponse createOrderForDistributor(AuthenticatedUser actor, OrderRequest request) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (request.customerId() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A customer account is required");
        }
        if (request.organizationId() != null
                && !actor.organizationId().equals(request.organizationId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Orders can only be created for the distributor's organization");
        }

        User customer = userRepository.findById(request.customerId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Customer not found"));
        if (customer.getRole() != UserRole.CUSTOMER || customer.getOrganization() != null
                || customer.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "The selected account is not an active customer");
        }
        if (!orderRepository.existsByOrganization_IdAndCustomer_Id(
                actor.organizationId(), customer.getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "The customer account is not linked to this distributor");
        }

        OrderRequest scopedRequest = new OrderRequest(
                actor.organizationId(),
                request.deliveryAddress(),
                request.deliveryLat(),
                request.deliveryLng(),
                request.items(),
                request.customerId());
        return createOrder(customer.getId(), scopedRequest);
    }

    @Transactional(readOnly = true)
    public List<OrganizationCustomerResponse> customersForDistributor(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        Map<UUID, OrganizationCustomerResponse> customers = new LinkedHashMap<>();
        for (Order order : orderRepository.findByOrganization_IdAndCustomer_RoleOrderByPlacedAtDesc(
                actor.organizationId(), UserRole.CUSTOMER)) {
            User customer = order.getCustomer();
            customers.putIfAbsent(customer.getId(), new OrganizationCustomerResponse(
                    customer.getId(),
                    customer.getFullName(),
                    customer.getFullName(),
                    order.getDeliveryAddress()));
        }
        return List.copyOf(customers.values());
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
        if (update.status() == OrderStatus.IN_TRANSIT) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Orders can enter transit only when their assigned delivery trip is dispatched");
        }
        if (update.status() == OrderStatus.READY_TO_PACK
                || update.status() == OrderStatus.AWAITING_DISPATCH) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Warehouse picking and packing must be confirmed through their warehouse actions");
        }
        if (!allowedNext.contains(update.status())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot transition from " + order.getStatus() + " to " + update.status());
        }

        if (update.status() == OrderStatus.CANCELLED || update.status() == OrderStatus.FAILED) {
            Payment payment = paymentRepository.findByOrderIdForUpdate(orderId).orElse(null);
            if (payment != null && payment.getStatus() == PaymentStatus.PENDING
                    && payment.getMpesaCheckoutRequestId() != null
                    && !payment.getMpesaCheckoutRequestId().isBlank()) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "This order has an in-flight M-Pesa payment and cannot be cancelled or failed until it is reconciled");
            }
        }

        User changedBy = userRepository.findById(changedById)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (changedBy.getRole() == UserRole.CUSTOMER && order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Customers may only cancel unpaid pending orders");
        }

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

    @Transactional
    public OrderResponse updateOrder(UUID orderId, OrderRequest request) {
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.CONFLICT, "Only pending orders can be edited");
        }
        if (request.organizationId() != null && order.getOrganization() != null && !order.getOrganization().getId().equals(request.organizationId())) {
            throw new ApiException(ErrorCode.CONFLICT, "The order organization cannot be changed");
        }
        Map<UUID, Integer> existing = order.getOrderItems().stream()
                .collect(Collectors.toMap(item -> item.getStockItem().getId(), OrderItem::getQuantity));
        if (request.items().stream().map(OrderItemRequest::stockItemId).distinct().count() != request.items().size()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Each stock item may appear only once in an order");
        }
        Map<UUID, Integer> requested = request.items().stream()
                .collect(Collectors.toMap(OrderItemRequest::stockItemId, OrderItemRequest::quantity));
        if (!existing.equals(requested)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Order items cannot be changed after reservation; update delivery details only");
        }
        order.setDeliveryAddress(request.deliveryAddress());
        order.setDeliveryLat(request.deliveryLat());
        order.setDeliveryLng(request.deliveryLng());
        return mapToResponse(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderStatsResponse stats(AuthenticatedUser actor) {
        if (actor == null || actor.role() == null) throw new ApiException(ErrorCode.UNAUTHENTICATED);
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            long count = switch (actor.role()) {
                case SUPER_ADMIN -> orderRepository.countByStatus(status);
                case CUSTOMER -> orderRepository.countByCustomer_IdAndStatus(actor.userId(), status);
                case DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF -> actor.organizationId() == null ? 0
                        : orderRepository.countByOrganization_IdAndStatus(actor.organizationId(), status);
                default -> throw new ApiException(ErrorCode.ACCESS_DENIED);
            };
            counts.put(status, count);
        }
        BigDecimal total = switch (actor.role()) {
            case SUPER_ADMIN -> orderRepository.sumTotalAmount();
            case CUSTOMER -> orderRepository.sumTotalAmountByCustomerId(actor.userId());
            case DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF -> actor.organizationId() == null ? BigDecimal.ZERO
                    : orderRepository.sumTotalAmountByOrganizationId(actor.organizationId());
            default -> throw new ApiException(ErrorCode.ACCESS_DENIED);
        };
        return new OrderStatsResponse(counts, total == null ? BigDecimal.ZERO : total);
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
        // Keep paid stock reserved until the assigned delivery trip is dispatched.
        return orderRepository.save(order);
    }

    @Transactional
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

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING
                && payment.getMpesaCheckoutRequestId() != null
                && !payment.getMpesaCheckoutRequestId().isBlank()) {
            // A provider-accepted STK request is still eligible for a late
            // callback. Keep the expired deadline so the reconciliation job
            // retries the provider query on a later scan.
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

    @Transactional
    public OrderResponse dispatchFromTrip(AuthenticatedUser actor, UUID orderId) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (!actor.organizationId().equals(order.getOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (order.getStatus() != OrderStatus.AWAITING_DISPATCH) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only an order ready for dispatch can enter transit");
        }
        User changedBy = userRepository.findById(actor.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        OrderStatusHistory history = new OrderStatusHistory();
        history.setFromStatus(order.getStatus());
        history.setToStatus(OrderStatus.IN_TRANSIT);
        history.setChangedBy(changedBy);
        history.setNote("Dispatched with assigned delivery trip");
        order.addStatusHistory(history);
        commitReservations(order, changedBy);
        order.setStatus(OrderStatus.IN_TRANSIT);
        return mapToResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse pickOrderItem(AuthenticatedUser actor, UUID orderId,
                                       PickOrderItemRequest request) {
        Order order = findWarehouseOrderForUpdate(actor, orderId);
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only paid orders awaiting picking can be picked");
        }
        OrderItem line = order.getOrderItems().stream()
                .filter(item -> item.getId().equals(request.orderItemId()))
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order item not found"));
        if (request.pickedQuantity() < line.getPickedQuantity()
                || request.pickedQuantity() > line.getQuantity()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Picked quantity must be between the already picked and requested quantities");
        }
        if (request.pickedQuantity() == line.getPickedQuantity()) return mapToResponse(order);
        OrderStatus previous = order.getStatus();
        line.setPickedQuantity(request.pickedQuantity());
        boolean complete = order.getOrderItems().stream()
                .allMatch(item -> item.getPickedQuantity() == item.getQuantity());
        order.setStatus(complete ? OrderStatus.READY_TO_PACK : OrderStatus.PROCESSING);
        recordWarehouseHistory(order, actor, previous, order.getStatus(),
                "Picked " + request.pickedQuantity() + " of " + line.getQuantity()
                        + " units for item " + line.getId());
        return mapToResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponse packOrder(AuthenticatedUser actor, UUID orderId, PackOrderRequest request) {
        if (request.packageCount() <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Package count must be a positive integer");
        }
        Order order = findWarehouseOrderForUpdate(actor, orderId);
        if (order.getStatus() == OrderStatus.AWAITING_DISPATCH
                && order.getPackageCount() == request.packageCount()) return mapToResponse(order);
        if (order.getStatus() != OrderStatus.READY_TO_PACK
                || order.getOrderItems().stream().anyMatch(item -> item.getPickedQuantity() != item.getQuantity())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "All order quantities must be picked before packing");
        }
        OrderStatus previous = order.getStatus();
        order.getOrderItems().forEach(item -> item.setPackedQuantity(item.getPickedQuantity()));
        order.setPackageCount(request.packageCount());
        order.setStatus(OrderStatus.AWAITING_DISPATCH);
        recordWarehouseHistory(order, actor, previous, order.getStatus(),
                "Packed into " + request.packageCount() + " package(s)");
        return mapToResponse(orderRepository.save(order));
    }

    private Order findWarehouseOrderForUpdate(AuthenticatedUser actor, UUID orderId) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (!actor.organizationId().equals(order.getOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return order;
    }

    private void recordWarehouseHistory(Order order, AuthenticatedUser actor, OrderStatus previous,
                                        OrderStatus next, String note) {
        User user = userRepository.findById(actor.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        OrderStatusHistory history = new OrderStatusHistory();
        history.setFromStatus(previous);
        history.setToStatus(next);
        history.setChangedBy(user);
        history.setNote(note);
        order.addStatusHistory(history);
    }

    private void commitReservations(Order order, User performedBy) {
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
                deductStoredLocations(stockItem.getId(), item.getQuantity());
                StockMovement movement = new StockMovement();
                movement.setStockItem(stockItem);
                movement.setMovementType(StockMovementType.SALE_OUT);
                movement.setQuantityDelta(-item.getQuantity());
                movement.setReferenceType("CUSTOMER_ORDER");
                movement.setReferenceId(order.getId());
                movement.setPerformedBy(performedBy);
                movement.setNote("Order dispatched");
                stockMovementRepository.save(movement);
            }
        }
    }

    private void deductStoredLocations(UUID stockItemId, int quantity) {
        int remaining = quantity;
        for (StockLocation location : stockLocationRepository.findAllForUpdate(stockItemId)) {
            if (remaining == 0) break;
            int deducted = Math.min(remaining, location.getQuantity());
            int next = location.getQuantity() - deducted;
            remaining -= deducted;
            if (next == 0) stockLocationRepository.delete(location);
            else {
                location.setQuantity(next);
                stockLocationRepository.save(location);
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

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAllWithDetails(pageable).map(this::mapToResponse);
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream().map(i -> new OrderItemResponse(
                i.getId(),
                i.getStockItem().getId(),
                i.getQuantity(),
                i.getPickedQuantity(),
                i.getPackedQuantity(),
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
                order.getPackageCount(),
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
