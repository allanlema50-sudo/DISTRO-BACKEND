package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrderService;
import com.example.distrobackend.service.OtpService;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OtpService otpService;

    // -----------------------------------------------------------------------
    // GET /api/v1/orders
    // - CUSTOMER           → their own orders only
    // - DISTRIBUTOR_ADMIN,
    //   DISTRIBUTOR_STAFF  → orders belonging to their organisation
    // - everyone else      → 403 (MANUFACTURER_* use Trips/Restock, DRIVER uses Trips)
    // -----------------------------------------------------------------------
    @GetMapping
    public Page<OrderResponse> listOrders(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PageableDefault(size = 20) Pageable pageable) {

        return switch (me.role()) {
            case CUSTOMER ->
                    orderService.getOrdersForCustomer(me.userId(), pageable);
            case DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF ->
                    orderService.getOrdersForOrganization(me.organizationId(), pageable);
            default ->
                    throw new ApiException(ErrorCode.ACCESS_DENIED,
                            "This endpoint is not available for your role");
        };
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/orders
    // - Only CUSTOMER may place an order; customer identity comes from the
    //   JWT principal, never from the request body.
    // -----------------------------------------------------------------------
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @AuthenticationPrincipal AuthenticatedUser me,
            @Valid @RequestBody OrderRequest request) {

        if (me.role() != UserRole.CUSTOMER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Only customers may place orders");
        }
        return orderService.createOrder(me.userId(), request);
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/orders/{orderId}
    // - CUSTOMER           → only if order.customerId == me.userId()
    // - DISTRIBUTOR_ADMIN,
    //   DISTRIBUTOR_STAFF  → only if order.organizationId == me.organizationId()
    // - everyone else      → 403
    // -----------------------------------------------------------------------
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID orderId) {

        OrderResponse order = orderService.getOrder(orderId);
        checkOrderAccess(me, order);
        return order;
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/orders/{orderId}/items
    // Same ownership rules as getOrder.
    // -----------------------------------------------------------------------
    @GetMapping("/{orderId}/items")
    public List<OrderItemResponse> getOrderItems(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID orderId) {

        OrderResponse order = orderService.getOrder(orderId);
        checkOrderAccess(me, order);
        return order.items();
    }

    // -----------------------------------------------------------------------
    // PUT /api/v1/orders/{orderId}
    // Full update — distributor staff / admin only (customers cannot edit an
    // order once placed; they can only cancel via PATCH status).
    // -----------------------------------------------------------------------
    @PutMapping("/{orderId}")
    public OrderResponse updateOrder(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderRequest request) {

        if (me.role() != UserRole.DISTRIBUTOR_ADMIN && me.role() != UserRole.DISTRIBUTOR_STAFF) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Only distributor staff may update order details");
        }

        OrderResponse existing = orderService.getOrder(orderId);
        if (!existing.organizationId().equals(me.organizationId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "You do not have access to this order");
        }

        // Order editing semantics (which fields are mutable, recalculation rules,
        // status constraints) are not yet confirmed. Throwing 501 so callers get
        // an explicit signal rather than a misleading 200 with unchanged data.
        // Order editing remains intentionally unsupported until its mutable fields,
        // recalculation rules, and status constraints are confirmed.
        throw new ApiException(ErrorCode.NOT_IMPLEMENTED,
                "Order editing is not yet supported. Use PATCH /{orderId}/status to transition order state.");
    }

    // -----------------------------------------------------------------------
    // PATCH /api/v1/orders/{orderId}/status
    // - CUSTOMER           → may cancel only their own unpaid PENDING order
    // - DISTRIBUTOR_ADMIN,
    //   DISTRIBUTOR_STAFF  → may perform any ALLOWED_TRANSITIONS move on their org's orders
    // - everyone else      → 403
    // -----------------------------------------------------------------------
    @PatchMapping("/{orderId}/status")
    public OrderResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderStatusUpdate update) {

        OrderResponse order = orderService.getOrder(orderId);

        if (me.role() == UserRole.CUSTOMER) {
            if (!order.customerId().equals(me.userId())) {
                throw new ApiException(ErrorCode.ACCESS_DENIED,
                        "You do not have access to this order");
            }
            if (update.status() != OrderStatus.CANCELLED) {
                throw new ApiException(ErrorCode.ACCESS_DENIED,
                        "Customers may only cancel orders");
            }
            if (order.status() != OrderStatus.PENDING) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Customers may only cancel unpaid pending orders");
            }
        } else if (me.role() == UserRole.DISTRIBUTOR_ADMIN
                || me.role() == UserRole.DISTRIBUTOR_STAFF) {
            if (!order.organizationId().equals(me.organizationId())) {
                throw new ApiException(ErrorCode.ACCESS_DENIED,
                        "You do not have access to this order");
            }
            // Staff may perform any transition that ALLOWED_TRANSITIONS permits;
            // the service layer validates the specific move and throws BAD_REQUEST
            // if it is illegal for the current status.
        } else {
            // MANUFACTURER_*, DRIVER, and any future roles not explicitly listed above
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "This endpoint is not available for your role");
        }

        return orderService.updateOrderStatus(orderId, me.userId(), update);
    }

    @PostMapping("/{orderId}/delivery-otp")
    public void requestDeliveryOtp(@AuthenticationPrincipal AuthenticatedUser me,
                                   @PathVariable UUID orderId) {
        OrderResponse order = orderService.getOrder(orderId);
        if (!order.customerId().equals(me.userId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (order.status() != OrderStatus.IN_TRANSIT) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Delivery OTP is available only for in-transit orders");
        }
        otpService.issue(orderService.getCustomer(orderId), OtpPurpose.DELIVERY_CONFIRMATION);
    }

    // -----------------------------------------------------------------------
    // Shared ownership check used by getOrder and getOrderItems
    // -----------------------------------------------------------------------
    private void checkOrderAccess(AuthenticatedUser me, OrderResponse order) {
        switch (me.role()) {
            case CUSTOMER -> {
                if (!order.customerId().equals(me.userId())) {
                    throw new ApiException(ErrorCode.ACCESS_DENIED,
                            "You do not have access to this order");
                }
            }
            case DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF -> {
                if (!order.organizationId().equals(me.organizationId())) {
                    throw new ApiException(ErrorCode.ACCESS_DENIED,
                            "You do not have access to this order");
                }
            }
            default ->
                    throw new ApiException(ErrorCode.ACCESS_DENIED,
                            "This endpoint is not available for your role");
        }
    }
}
