package com.example.distrobackend.controller;

import com.example.distrobackend.dto.PurchaseOrderDecisionRequest;
import com.example.distrobackend.dto.PurchaseOrderResponse;
import com.example.distrobackend.dto.PurchaseOrderSettlementResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manufacturer/purchase-orders")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF')")
@RequiredArgsConstructor
public class ManufacturerPurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public Page<PurchaseOrderResponse> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PageableDefault(size = 20) Pageable pageable) {
        return purchaseOrderService.listForManufacturer(actor, pageable);
    }

    @GetMapping("/{purchaseOrderId}")
    public PurchaseOrderResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable UUID purchaseOrderId) {
        return purchaseOrderService.getForManufacturer(actor, purchaseOrderId);
    }

    @PatchMapping("/{purchaseOrderId}/decision")
    @PreAuthorize("hasRole('MANUFACTURER_ADMIN')")
    public PurchaseOrderResponse decide(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID purchaseOrderId,
                                        @Valid @RequestBody PurchaseOrderDecisionRequest request) {
        return purchaseOrderService.decide(actor, purchaseOrderId, request);
    }

    @GetMapping("/{purchaseOrderId}/settlements")
    public List<PurchaseOrderSettlementResponse> settlements(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID purchaseOrderId) {
        return purchaseOrderService.settlements(actor, purchaseOrderId);
    }
}
