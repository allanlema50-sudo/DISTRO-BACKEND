package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateShipmentRequest;
import com.example.distrobackend.dto.ShipmentResponse;
import com.example.distrobackend.dto.ShipmentStatusRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/manufacturer/shipments")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF')")
@RequiredArgsConstructor
public class ManufacturerShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF')")
    public ShipmentResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                   @Valid @RequestBody CreateShipmentRequest request) {
        return shipmentService.create(actor, request);
    }

    @GetMapping
    public Page<ShipmentResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @PageableDefault(size = 20) Pageable pageable) {
        return shipmentService.listForManufacturer(actor, pageable);
    }

    @GetMapping("/{shipmentId}")
    public ShipmentResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable UUID shipmentId) {
        return shipmentService.get(actor, shipmentId);
    }

    @PatchMapping("/{shipmentId}/status")
    public ShipmentResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable UUID shipmentId,
                                         @Valid @RequestBody ShipmentStatusRequest request) {
        return shipmentService.updateStatus(actor, shipmentId, request);
    }
}
