package com.example.distrobackend.controller;

import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping("/organizations/{organizationId}/items")
    @PreAuthorize("isAuthenticated()")
    public List<StockItemResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID organizationId,
                                        @RequestParam(defaultValue = "true") boolean activeOnly) {
        return stockService.list(actor, organizationId, activeOnly);
    }

    @GetMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public StockItemResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @PathVariable UUID itemId) {
        if (actor == null || actor.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "An organization context is required");
        }
        return stockService.get(actor, actor.organizationId(), itemId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')")
    public StockItemResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @Valid @RequestBody CreateStockItemRequest request) {
        return stockService.create(actor, request);
    }

    @PatchMapping("/{itemId}")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')")
    public StockItemResponse update(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID itemId,
                                    @Valid @RequestBody UpdateStockItemRequest request) {
        return stockService.update(actor, itemId, request);
    }

    @PostMapping("/{itemId}/adjustments")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')")
    public StockItemResponse adjust(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID itemId,
                                    @Valid @RequestBody StockAdjustmentRequest request) {
        return stockService.adjust(actor, itemId, request);
    }

    @GetMapping("/{itemId}/movements")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')")
    public List<StockMovementResponse> movements(@AuthenticationPrincipal AuthenticatedUser actor,
                                                 @PathVariable UUID itemId) {
        return stockService.movements(actor, itemId);
    }
}
