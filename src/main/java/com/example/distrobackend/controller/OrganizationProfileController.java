package com.example.distrobackend.controller;

import com.example.distrobackend.dto.OrganizationResponse;
import com.example.distrobackend.dto.UpdateOrganizationProfileRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrganizationProfileService;
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
@RequestMapping("/api/v1/organization")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
@RequiredArgsConstructor
public class OrganizationProfileController {

    private final OrganizationProfileService organizationProfileService;

    @GetMapping
    public OrganizationResponse get(@AuthenticationPrincipal AuthenticatedUser actor) {
        return organizationProfileService.get(actor);
    }

    @PatchMapping
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN') and @tenantAccess.hasOrganization(authentication)")
    public OrganizationResponse update(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody UpdateOrganizationProfileRequest request) {
        return organizationProfileService.update(actor, request);
    }
}
