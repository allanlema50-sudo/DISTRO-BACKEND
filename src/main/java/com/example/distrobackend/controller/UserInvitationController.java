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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN') and @tenantAccess.hasOrganization(authentication)")
@RequiredArgsConstructor
public class UserInvitationController {

    private final InvitationService invitationService;

    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @Valid @RequestBody CreateInvitationRequest request) {
        return invitationService.create(actor, request);
    }
}
