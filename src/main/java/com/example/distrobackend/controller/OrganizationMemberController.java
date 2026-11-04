package com.example.distrobackend.controller;

import com.example.distrobackend.dto.UpdateUserStatusRequest;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.dto.CreateInvitationRequest;
import com.example.distrobackend.dto.CreateOrganizationMemberInvitationRequest;
import com.example.distrobackend.dto.InvitationResponse;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.InvitationService;
import com.example.distrobackend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/organization/members", "/api/v1/members"})
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN') and @tenantAccess.hasOrganization(authentication)")
@RequiredArgsConstructor
public class OrganizationMemberController {

    private final UserService userService;
    private final InvitationService invitationService;

    @PostMapping("/invitations")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public InvitationResponse invite(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateOrganizationMemberInvitationRequest request) {
        UserRole staffRole = actor.organizationType() == Organizationtype.MANUFACTURER
                ? UserRole.MANUFACTURER_STAFF : UserRole.DISTRIBUTOR_STAFF;
        return invitationService.create(actor, new CreateInvitationRequest(
                request.fullName(), request.email(), request.phoneNumber(), staffRole,
                null, request.jobTitle(), request.warehouseId()));
    }

    @GetMapping
    public Page<UserResponse> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            Pageable pageable) {
        return userService.listForOrganization(actor, pageable);
    }

    @PatchMapping("/{userId}/status")
    public UserResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return userService.updateStatusForOrganization(actor, userId, request);
    }
}
