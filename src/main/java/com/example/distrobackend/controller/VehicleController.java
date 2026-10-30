package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.enums.VehicleStatus;
import com.example.distrobackend.dto.CreateVehicleRequest;
import com.example.distrobackend.dto.UpdateVehicleRequest;
import com.example.distrobackend.dto.VehicleResponse;
import com.example.distrobackend.dto.VehicleStatusUpdateRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public Page<VehicleResponse> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID organizationId,
            Pageable pageable) {
        VehicleStatus requestedStatus = parseStatus(status);
        boolean available = "AVAILABLE".equalsIgnoreCase(status);
        return vehicleService.list(actor, organizationId, requestedStatus, available, pageable);
    }

    @GetMapping("/{vehicleId}")
    public VehicleResponse get(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID vehicleId) {
        return vehicleService.get(actor, vehicleId);
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public VehicleResponse create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateVehicleRequest request) {
        return vehicleService.create(actor, request);
    }

    @org.springframework.web.bind.annotation.RequestMapping(
            value = "/{vehicleId}",
            method = {org.springframework.web.bind.annotation.RequestMethod.PATCH,
                    org.springframework.web.bind.annotation.RequestMethod.PUT})
    public VehicleResponse update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return vehicleService.update(actor, vehicleId, request);
    }

    @PatchMapping("/{vehicleId}/status")
    public VehicleResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID vehicleId,
            @Valid @RequestBody VehicleStatusUpdateRequest request) {
        return vehicleService.updateStatus(actor, vehicleId, request);
    }

    private static VehicleStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "AVAILABLE".equalsIgnoreCase(status)) return null;
        try {
            return VehicleStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new com.example.distrobackend.Exception.ApiException(
                    com.example.distrobackend.Exception.ErrorCode.BAD_REQUEST, "Invalid vehicle status");
        }
    }
}
