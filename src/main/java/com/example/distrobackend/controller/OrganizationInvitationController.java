package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateInvitationRequest;
import com.example.distrobackend.dto.InvitationResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organization/invitations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN')")
public class OrganizationInvitationController {
    private final InvitationService invitationService;

    @GetMapping
    public List<InvitationResponse> list(@AuthenticationPrincipal AuthenticatedUser actor) {
        return invitationService.list(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateInvitationRequest request) {
        return invitationService.create(actor, request);
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable java.util.UUID invitationId) {
        invitationService.revoke(actor, invitationId);
    }
}
