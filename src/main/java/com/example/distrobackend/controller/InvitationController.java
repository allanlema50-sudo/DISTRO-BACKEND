package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateInvitationRequest;
import com.example.distrobackend.dto.InvitationResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','MANUFACTURER_ADMIN')")
    public InvitationResponse createInvitation(
            @Valid @RequestBody CreateInvitationRequest request,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return invitationService.createInvitation(request, me);
    }
}