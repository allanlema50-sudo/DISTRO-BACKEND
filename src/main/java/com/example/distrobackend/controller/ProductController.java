package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateStockItemRequest;
import com.example.distrobackend.dto.ProductResponse;
import com.example.distrobackend.dto.UpdateStockItemRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final StockService stockService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<ProductResponse> list(
            @RequestParam(required = false) String category, Pageable pageable) {
        return stockService.catalog(category, pageable);
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public List<String> categories() {
        return stockService.categories();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public ProductResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateStockItemRequest request) {
        return ProductResponse.from(stockService.create(user, request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public ProductResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStockItemRequest request) {
        return ProductResponse.from(stockService.update(user, id, request));
    }
}
