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
            @RequestParam(required = false) VehicleStatus status,
            Pageable pageable) {
        return vehicleService.list(actor, status, pageable);
    }

    @GetMapping("/{vehicleId}")
    public VehicleResponse get(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID vehicleId) {
        return vehicleService.get(actor, vehicleId);
    }

    @PostMapping
    public VehicleResponse create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateVehicleRequest request) {
        return vehicleService.create(actor, request);
    }

    @PatchMapping("/{vehicleId}")
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
}
