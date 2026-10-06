package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.dto.organization.CreateOrganizationRequest;
import com.example.distrobackend.dto.organization.OrganizationResponse;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;


    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','SUPER_ADMIN','MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN')")
    public List<OrganizationResponse> getAllOrganizations(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        if (!isPlatformAdmin(me)) {
            if (me.organizationId() == null) {
                throw new AccessDeniedException("User is not associated with an organization");
            }
            return List.of(OrganizationResponse.from(
                    organizationService.getOrganization(me.organizationId())));
        }

        return organizationService
                .getAllOrganizations()
                .stream()
                .map(OrganizationResponse::from)
                .toList();
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','SUPER_ADMIN','MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN')")
    public OrganizationResponse getOrganization(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser me
    ) {

        if (!isPlatformAdmin(me) && !Objects.equals(me.organizationId(), id)) {
            throw new AccessDeniedException("You cannot access another organization's record");
        }

        Organization organization =
                organizationService.getOrganization(id);

        return OrganizationResponse.from(organization);
    }


    @PostMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','SUPER_ADMIN')")
    public OrganizationResponse createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request
    ) {

        Organization organization =
                organizationService.createOrganization(
                        request.name(),
                        request.type()
                );

        return OrganizationResponse.from(organization);
    }

    private boolean isPlatformAdmin(AuthenticatedUser user) {
        return user.role() == UserRole.PLATFORM_ADMIN || user.role() == UserRole.SUPER_ADMIN;
    }
}
