package com.example.distrobackend.controller;

import com.example.distrobackend.dto.OrderResponse;
import com.example.distrobackend.dto.OrderStatsResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@PreAuthorize("""
        hasAnyRole(
            'PLATFORM_ADMIN',
            'SUPER_ADMIN',
            'MANUFACTURER_ADMIN',
            'MANUFACTURER_STAFF',
            'DISTRIBUTOR_ADMIN',
            'DISTRIBUTOR_STAFF',
            'CUSTOMER'
        )
        """)
public class OrderController {

    private final OrderService orderService;

    // =========================================================
    // GET ORDERS
    // =========================================================

    @GetMapping
    public Page<OrderResponse> getOrders(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search
    ) {
        return orderService.getOrders(
                me,
                page,
                size,
                search
        );
    }

    // =========================================================
    // GET ORDER BY ID
    // =========================================================

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return orderService.getOrder(
                orderId,
                me
        );
    }

    // =========================================================
    // GET ORDER STATS
    // =========================================================

    @GetMapping("/stats")
    public OrderStatsResponse getStats(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return orderService.getStats(me);
    }
}