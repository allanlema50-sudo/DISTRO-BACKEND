package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateWarehouseRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DISTRIBUTOR_ADMIN') and @tenantAccess.hasOrganization(authentication)")
    public WarehouseResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateWarehouseRequest request) {
        return warehouseService.create(user, request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<WarehouseResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
        return warehouseService.list(user, pageable);
    }
}
