package com.example.distrobackend.controller;

import com.example.distrobackend.dto.ShipmentResponse;
import com.example.distrobackend.dto.ReceiveShipmentRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments")
@PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @GetMapping
    public Page<ShipmentResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @PageableDefault(size = 20) Pageable pageable) {
        return shipmentService.listForDistributor(actor, pageable);
    }

    @GetMapping("/{shipmentId}")
    public ShipmentResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable UUID shipmentId) {
        return shipmentService.get(actor, shipmentId);
    }

    @PostMapping("/{shipmentId}/receive")
    public ShipmentResponse receive(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID shipmentId,
                                    @Valid @RequestBody(required = false) ReceiveShipmentRequest request) {
        return shipmentService.receive(actor, shipmentId, request == null ? null : request.note());
    }
}
