package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreatePurchaseOrderRequest;
import com.example.distrobackend.dto.PurchaseOrderResponse;
import com.example.distrobackend.dto.PurchaseOrderSettlementResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return purchaseOrderService.create(actor, request);
    }

    @GetMapping
    public Page<PurchaseOrderResponse> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PageableDefault(size = 20) Pageable pageable) {
        return purchaseOrderService.listForDistributor(actor, pageable);
    }

    @GetMapping("/{purchaseOrderId}")
    public PurchaseOrderResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable UUID purchaseOrderId) {
        return purchaseOrderService.getForDistributor(actor, purchaseOrderId);
    }

    @GetMapping("/{purchaseOrderId}/settlements")
    public List<PurchaseOrderSettlementResponse> settlements(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID purchaseOrderId) {
        return purchaseOrderService.settlements(actor, purchaseOrderId);
    }
}
