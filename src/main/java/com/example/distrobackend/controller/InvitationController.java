package com.example.distrobackend.controller;

import com.example.distrobackend.dto.AcceptInvitationRequest;
import com.example.distrobackend.dto.CreateInvitationRequest;
import com.example.distrobackend.dto.InvitationPublicResponse;
import com.example.distrobackend.dto.InvitationResponse;
import com.example.distrobackend.dto.AuthResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AuthService;
import com.example.distrobackend.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;
    private final AuthService authService;

    // ================================================================
    // CREATE INVITATION
    // ================================================================

    @PostMapping
    @PreAuthorize("""
            hasAnyRole(
                'PLATFORM_ADMIN',
                'DISTRIBUTOR_ADMIN',
                'MANUFACTURER_ADMIN'
            )
            """)
    public InvitationResponse createInvitation(
            @Valid @RequestBody CreateInvitationRequest request,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        return invitationService.createInvitation(
                request,
                me
        );
    }

    // ================================================================
    // VIEW INVITATION
    // PUBLIC
    // ================================================================

    @GetMapping("/{token}")
    public InvitationPublicResponse getInvitation(
            @PathVariable String token
    ) {

        return invitationService.getInvitation(
                token
        );
    }

    // ================================================================
    // ACCEPT INVITATION
    // PUBLIC
    // ================================================================

    @PostMapping("/{token}/accept")
    public AuthResponse acceptInvitation(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest request
    ) {

        return authService.acceptInvitation(
                token,
                request
        );
    }
}