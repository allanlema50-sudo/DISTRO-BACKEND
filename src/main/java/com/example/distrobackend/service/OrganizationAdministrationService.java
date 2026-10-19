package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreateOrganizationRequest;
import com.example.distrobackend.dto.OrganizationResponse;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationAdministrationService {

    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public Page<OrganizationResponse> list(AuthenticatedUser actor, Pageable pageable) {
        requirePlatformAdmin(actor);
        return organizationRepository.findAll(pageable).map(OrganizationResponse::from);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(AuthenticatedUser actor, UUID organizationId) {
        requirePlatformAdmin(actor);
        return organizationRepository.findById(organizationId)
                .map(OrganizationResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
    }

    @Transactional
    public OrganizationResponse create(AuthenticatedUser actor, CreateOrganizationRequest request) {
        requirePlatformAdmin(actor);
        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setEmail(request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT));
        organization.setType(request.type());
        return OrganizationResponse.from(organizationRepository.save(organization));
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
