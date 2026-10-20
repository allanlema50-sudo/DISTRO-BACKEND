package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateOrganizationRequest;
import com.example.distrobackend.dto.OrganizationResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrganizationAdministrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationAdministrationService organizationService;

    @GetMapping
    public Page<OrganizationResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                           Pageable pageable) {
        return organizationService.list(actor, pageable);
    }

    @GetMapping("/{organizationId}")
    public OrganizationResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID organizationId) {
        return organizationService.get(actor, organizationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @Valid @RequestBody CreateOrganizationRequest request) {
        return organizationService.create(actor, request);
    }
}
