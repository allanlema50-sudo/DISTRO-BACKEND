package com.example.distrobackend.controller;

import com.example.distrobackend.dto.OrganizationNotificationPreferencesRequest;
import com.example.distrobackend.dto.OrganizationNotificationPreferencesResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrganizationNotificationPreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organization/notification-preferences")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF') and @tenantAccess.hasOrganization(authentication)")
@RequiredArgsConstructor
public class OrganizationNotificationPreferencesController {
    private final OrganizationNotificationPreferencesService preferencesService;

    @GetMapping
    public OrganizationNotificationPreferencesResponse get(
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return preferencesService.get(actor);
    }

    @PatchMapping
    public OrganizationNotificationPreferencesResponse update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody OrganizationNotificationPreferencesRequest request) {
        return preferencesService.update(actor, request);
    }
}
