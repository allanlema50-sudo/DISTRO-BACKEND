package com.example.distrobackend.controller;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.dto.organization.CreateOrganizationRequest;
import com.example.distrobackend.dto.organization.OrganizationResponse;
import com.example.distrobackend.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;


    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN')")
    public List<OrganizationResponse> getAllOrganizations() {

        return organizationService
                .getAllOrganizations()
                .stream()
                .map(OrganizationResponse::from)
                .toList();
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN')")
    public OrganizationResponse getOrganization(
            @PathVariable UUID id
    ) {

        Organization organization =
                organizationService.getOrganization(id);

        return OrganizationResponse.from(organization);
    }


    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
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
}