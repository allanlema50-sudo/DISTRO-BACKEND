package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateWarehouseRequest;
import com.example.distrobackend.dto.UpdateWarehouseRequest;
import com.example.distrobackend.dto.UpdateWarehouseStatusRequest;
import com.example.distrobackend.dto.WarehouseResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN') and @tenantAccess.hasOrganization(authentication)")
    public WarehouseResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateWarehouseRequest request) {
        return warehouseService.create(user, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<WarehouseResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
        return warehouseService.list(user, pageable);
    }

    @GetMapping("/{warehouseId}")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public WarehouseResponse get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID warehouseId) {
        return warehouseService.get(user, warehouseId);
    }

    @PatchMapping("/{warehouseId}")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN') and @tenantAccess.hasOrganization(authentication)")
    public WarehouseResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID warehouseId,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        return warehouseService.update(user, warehouseId, request);
    }

    @PatchMapping("/{warehouseId}/status")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN') and @tenantAccess.hasOrganization(authentication)")
    public WarehouseResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID warehouseId,
            @Valid @RequestBody UpdateWarehouseStatusRequest request) {
        return warehouseService.updateStatus(user, warehouseId, request);
    }
}
