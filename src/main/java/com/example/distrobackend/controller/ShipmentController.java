package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.enums.ShipmentStatus;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/shipments") @RequiredArgsConstructor
public class ShipmentController {
    private final ShipmentService service;
    @GetMapping public Page<ShipmentResponse> list(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required=false) ShipmentStatus status, @RequestParam(required=false) UUID organizationId, Pageable pageable) { return service.list(me,status,organizationId,pageable); }
    @GetMapping("/{shipmentId}") public ShipmentResponse get(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable UUID shipmentId) { return service.get(me,shipmentId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ShipmentResponse create(@AuthenticationPrincipal AuthenticatedUser me,@Valid @RequestBody ShipmentRequest request) { return service.create(me,request); }
    @PutMapping("/{shipmentId}") public ShipmentResponse update(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable UUID shipmentId,@Valid @RequestBody ShipmentRequest request) { return service.update(me,shipmentId,request); }
    @PatchMapping("/{shipmentId}/status") public ShipmentResponse status(@AuthenticationPrincipal AuthenticatedUser me,@PathVariable UUID shipmentId,@Valid @RequestBody ShipmentStatusUpdate request) { return service.updateStatus(me,shipmentId,request.status(),request.note()); }
}
