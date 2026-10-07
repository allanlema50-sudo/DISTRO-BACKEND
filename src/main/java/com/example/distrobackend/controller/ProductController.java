package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateStockItemRequest;
import com.example.distrobackend.dto.StockItemResponse;
import com.example.distrobackend.dto.UpdateStockItemRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Product catalog facade over organization-owned stock items. */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final StockService stockService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<StockItemResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @RequestParam UUID organizationId) {
        return stockService.list(actor, organizationId, true);
    }

    @GetMapping("/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public StockItemResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @PathVariable UUID itemId,
                                 @RequestParam UUID organizationId) {
        return stockService.get(actor, organizationId, itemId);
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
}
