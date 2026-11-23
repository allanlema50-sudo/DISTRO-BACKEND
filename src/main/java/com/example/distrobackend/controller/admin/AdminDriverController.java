package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.DriverApprovalRequest;
import com.example.distrobackend.dto.DriverProfileChangeDecisionRequest;
import com.example.distrobackend.dto.DriverResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/drivers/{driverId}")
@RequiredArgsConstructor
public class AdminDriverController {
    private final DriverService driverService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PatchMapping("/approval")
    public DriverResponse updateApproval(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable UUID driverId,
                                         @Valid @RequestBody DriverApprovalRequest request) {
        return driverService.approve(actor, driverId, request.status());
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/login-email")
    public DriverResponse sendLoginEmail(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable UUID driverId) {
        return driverService.sendLoginEmail(actor, driverId);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PatchMapping("/profile-change")
    public DriverResponse reviewProfileChange(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @PathVariable UUID driverId,
                                              @Valid @RequestBody DriverProfileChangeDecisionRequest request) {
        return driverService.reviewProfileChange(actor, driverId, request.decision());
    }
}
