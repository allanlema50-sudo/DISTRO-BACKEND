package com.example.distrobackend.controller;

import com.example.distrobackend.dto.UpdateUserStatusRequest;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.security.AuthenticatedUser;
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
@RequestMapping("/api/v1/organization/members")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN') and @tenantAccess.hasOrganization(authentication)")
@RequiredArgsConstructor
public class OrganizationMemberController {

    private final UserService userService;

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
