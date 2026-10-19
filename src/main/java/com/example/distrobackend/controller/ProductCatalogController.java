package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateStockItemRequest;
import com.example.distrobackend.dto.ProductResponse;
import com.example.distrobackend.dto.ProductStatsResponse;
import com.example.distrobackend.dto.ProductStatusRequest;
import com.example.distrobackend.dto.StockItemResponse;
import com.example.distrobackend.dto.UpdateStockItemRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AdminStockService;
import com.example.distrobackend.service.ProductCatalogService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductCatalogController {

    private final StockService stockService;
    private final ProductCatalogService productCatalogService;
    private final AdminStockService adminStockService;

    @GetMapping
    public Page<ProductResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @RequestParam(required = false) String category,
                                      Pageable pageable) {
        return stockService.catalog(actor, category, pageable);
    }

    @GetMapping("/categories")
    public List<String> categories(@AuthenticationPrincipal AuthenticatedUser actor) {
        return stockService.categories(actor);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public ProductResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @Valid @RequestBody CreateStockItemRequest request) {
        return ProductResponse.from(stockService.create(actor, request));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public ProductResponse update(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable UUID productId,
                                  @Valid @RequestBody UpdateStockItemRequest request) {
        return ProductResponse.from(stockService.update(actor, productId, request));
    }

    @PatchMapping("/{productId}/status")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public ProductResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID productId,
                                        @Valid @RequestBody ProductStatusRequest request) {
        return ProductResponse.from(stockService.update(actor, productId,
                new UpdateStockItemRequest(null, null, null, null, request.active())));
    }

    @GetMapping("/stats")
    public ProductStatsResponse stats(@AuthenticationPrincipal AuthenticatedUser actor) {
        return productCatalogService.stats(actor);
    }

    @GetMapping("/{productId}")
    public ProductResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                               @PathVariable UUID productId) {
        return stockService.catalogItem(actor, productId);
    }

    @GetMapping("/{productId}/inventory")
    public StockItemResponse inventory(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @PathVariable UUID productId) {
        return productCatalogService.getInventory(actor, productId);
    }

    @PatchMapping("/{productId}/approval")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ProductResponse approval(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID productId,
                                    @Valid @RequestBody ProductStatusRequest request) {
        StockItemResponse updated = adminStockService.update(actor, productId,
                new UpdateStockItemRequest(null, null, null, null, request.active()));
        return ProductResponse.from(updated);
    }
}
