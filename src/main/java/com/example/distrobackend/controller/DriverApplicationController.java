package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.DriverApplicationDecisionRequest;
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

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/driver-applications")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class DriverApplicationController {
    private final DriverService driverService;

    @PatchMapping("/{driverId}/decision")
    public DriverResponse decide(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @PathVariable UUID driverId,
                                 @Valid @RequestBody DriverApplicationDecisionRequest request) {
        DriverApprovalStatus status = switch (request.decision().trim().toUpperCase(Locale.ROOT)) {
            case "APPROVED", "APPROVE" -> DriverApprovalStatus.APPROVED;
            case "REJECTED", "REJECT" -> DriverApprovalStatus.REJECTED;
            default -> throw new ApiException(ErrorCode.BAD_REQUEST,
                    "decision must be APPROVED or REJECTED");
        };
        return driverService.approve(actor, driverId, status);
    }
}
