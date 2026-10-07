package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateStockItemRequest;
import com.example.distrobackend.dto.CreateRestockRequest;
import com.example.distrobackend.dto.RestockRequestResponse;
import com.example.distrobackend.dto.StockAdjustmentRequest;
import com.example.distrobackend.dto.StockItemResponse;
import com.example.distrobackend.dto.StockMovementResponse;
import com.example.distrobackend.dto.UpdateStockItemRequest;
import com.example.distrobackend.dto.UpdateRestockRequestStatus;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping("/restock-request")
    @PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public RestockRequestResponse createRestockRequest(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateRestockRequest request) {
        return stockService.createRestockRequest(user, request);
    }

    @GetMapping("/restock-request")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<RestockRequestResponse> restockRequests(
            @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
        return stockService.restockRequests(user, pageable);
    }

    @PatchMapping("/restock-request/{id}/status")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public RestockRequestResponse updateRestockRequestStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRestockRequestStatus request) {
        return stockService.updateRestockRequestStatus(user, id, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<StockItemResponse> listRoot(
            @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
        return stockService.list(user, pageable);
    }

    @GetMapping("/{organizationId}/availability")
    @PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<StockItemResponse> availability(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID organizationId, Pageable pageable) {
        return stockService.availability(user, organizationId, pageable);
    }

    @GetMapping("/items")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<StockItemResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
        return stockService.list(user, pageable);
    }

    @GetMapping("/items/low-stock")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<StockItemResponse> listLowStock(
            @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
        return stockService.listLowStock(user, pageable);
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public StockItemResponse get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return stockService.get(user, id);
    }

    @PostMapping("/items")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public StockItemResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateStockItemRequest request) {
        return stockService.create(user, request);
    }

    @PatchMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public StockItemResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStockItemRequest request) {
        return stockService.update(user, id, request);
    }

    @PostMapping("/items/{id}/movements")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public StockMovementResponse adjust(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return stockService.adjust(user, id, request);
    }

    @GetMapping("/items/{id}/movements")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
        public Page<StockMovementResponse> movements(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            Pageable pageable) {
        return stockService.movements(user, id, pageable);
    }
}
