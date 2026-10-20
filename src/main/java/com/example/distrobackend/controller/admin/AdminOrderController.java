package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.OrderResponse;
import com.example.distrobackend.dto.OrderStatusUpdate;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    public Page<OrderResponse> list(Pageable pageable) {
        return orderService.getAllOrders(pageable);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return orderService.getOrder(orderId);
    }

    @PatchMapping("/{orderId}/status")
    public OrderResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @PathVariable UUID orderId,
                                      @Valid @RequestBody OrderStatusUpdate request) {
        return orderService.updateOrderStatus(orderId, actor.userId(), request);
    }
}
