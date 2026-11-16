package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Domain.enums.DriverApprovalStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreateDriverInvitationRequest;
import com.example.distrobackend.dto.DriverRegistrationRequest;
import com.example.distrobackend.dto.DriverOperationalStatusRequest;
import com.example.distrobackend.dto.DriverLocationRequest;
import com.example.distrobackend.dto.DriverResponse;
import com.example.distrobackend.dto.InvitationResponse;
import com.example.distrobackend.dto.UpdateDriverAvailabilityRequest;
import com.example.distrobackend.dto.UpdateDriverRequest;
import com.example.distrobackend.dto.UpdateUserStatusRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.DriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping
    public org.springframework.http.ResponseEntity<?> registerOrInvite(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody DriverRegistrationRequest request) {
        if (actor != null && (actor.role() == UserRole.MANUFACTURER_ADMIN
                || actor.role() == UserRole.DISTRIBUTOR_ADMIN)) {
            if (request.email() == null || request.email().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "An email address is required to invite a driver");
            }
            InvitationResponse invitation = driverService.invite(actor,
                    new CreateDriverInvitationRequest(request.fullName(), request.email(),
                            request.phoneNumber(), request.licenseNumber(), request.nationalId(),
                            request.licenseExpiryDate(), request.baseWarehouseId(), request.notes()));
            return org.springframework.http.ResponseEntity.status(HttpStatus.ACCEPTED).body(invitation);
        }
        DriverResponse driver = driverService.registerForReview(actor, request);
        return org.springframework.http.ResponseEntity.status(HttpStatus.CREATED).body(driver);
    }
    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InvitationResponse invite(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateDriverInvitationRequest request) {
        return driverService.invite(actor, request);
    }

    @PostMapping({"/{driverId}/invitation", "/{driverId}/invitation/resend"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InvitationResponse resendInvitation(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID driverId) {
        requireOrganizationAdmin(actor);
        return driverService.resendInvitation(actor, driverId);
    }

    @DeleteMapping("/{driverId}/invitation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID driverId) {
        requireOrganizationAdmin(actor);
        driverService.revokeInvitation(actor, driverId);
    }

    @GetMapping
    public Page<DriverResponse> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) UUID organizationId,
            Pageable pageable) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) requireOrganizationRole(actor, false);
        DriverStatusFilter statusFilter = parseStatus(status);
        return driverService.list(actor, organizationId, statusFilter.userStatus(),
                statusFilter.approvalStatus(), parseAvailability(availability), pageable);
    }

    @GetMapping("/{driverId}")
    public DriverResponse get(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID driverId) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) requireOrganizationRole(actor, true);
        return driverService.get(actor, driverId);
    }

    @PatchMapping("/{driverId}")
    public DriverResponse update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID driverId,
            @Valid @RequestBody UpdateDriverRequest request) {
        requireOrganizationAdmin(actor);
        return driverService.update(actor, driverId, request);
    }

    @PatchMapping("/{driverId}/status")
    public DriverResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID driverId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        requireOrganizationAdmin(actor);
        return driverService.updateStatus(actor, driverId, request);
    }

    @PatchMapping("/{driverId}/availability")
    public DriverResponse updateAvailability(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID driverId,
            @Valid @RequestBody UpdateDriverAvailabilityRequest request) {
        return driverService.updateAvailability(actor, driverId, request);
    }

    @PatchMapping("/{driverId}/operational-status")
    public DriverResponse updateOperationalStatus(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverOperationalStatusRequest request) {
        String status = request.status().trim().toUpperCase(java.util.Locale.ROOT);
        boolean available = switch (status) {
            case "AVAILABLE" -> true;
            case "OFF_DUTY", "UNAVAILABLE" -> false;
            default -> throw new ApiException(ErrorCode.BAD_REQUEST,
                    "status must be AVAILABLE or OFF_DUTY");
        };
        return driverService.updateAvailability(actor, driverId,
                new UpdateDriverAvailabilityRequest(available));
    }

    @PostMapping("/me/location")
    public DriverResponse recordLocation(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody DriverLocationRequest request) {
        return driverService.recordLocation(actor, request);
    }

    private static void requireOrganizationRole(AuthenticatedUser actor, boolean allowDriver) {
        if (actor == null) throw new ApiException(ErrorCode.ACCESS_DENIED);
        if (actor.role() == UserRole.DRIVER) {
            if (allowDriver) return;
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (actor.organizationId() == null
                || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.MANUFACTURER_STAFF
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static void requireOrganizationAdmin(AuthenticatedUser actor) {
        if (actor == null || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static DriverStatusFilter parseStatus(String status) {
        if (status == null || status.isBlank()) return new DriverStatusFilter(null, null);
        if ("APPROVED".equalsIgnoreCase(status)) {
            return new DriverStatusFilter(null, DriverApprovalStatus.APPROVED);
        }
        if ("PENDING_REVIEW".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
            return new DriverStatusFilter(null, DriverApprovalStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)));
        }
        try {
            return new DriverStatusFilter(
                    UserStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)), null);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid driver status");
        }
    }

    private static Boolean parseAvailability(String availability) {
        if (availability == null || availability.isBlank()) return null;
        return switch (availability.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "AVAILABLE" -> true;
            case "UNAVAILABLE" -> false;
            default -> throw new ApiException(ErrorCode.BAD_REQUEST,
                    "availability must be AVAILABLE or UNAVAILABLE");
        };
    }

    private record DriverStatusFilter(UserStatus userStatus, DriverApprovalStatus approvalStatus) {}
}
