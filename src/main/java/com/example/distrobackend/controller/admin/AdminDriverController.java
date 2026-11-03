package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.DriverApprovalRequest;
import com.example.distrobackend.dto.DriverResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/drivers")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminDriverController {
    private final DriverService driverService;

    @PatchMapping("/{driverId}/approval")
    public DriverResponse updateApproval(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable UUID driverId,
                                         @Valid @RequestBody DriverApprovalRequest request) {
        return driverService.approve(actor, driverId, request.status());
    }
}
