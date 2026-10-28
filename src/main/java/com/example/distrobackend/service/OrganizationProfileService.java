package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.OrganizationResponse;
import com.example.distrobackend.dto.UpdateOrganizationProfileRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationProfileService {

    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public OrganizationResponse get(AuthenticatedUser actor) {
        return OrganizationResponse.from(loadAuthorizedOrganization(actor, false));
    }

    @Transactional
    public OrganizationResponse update(AuthenticatedUser actor, UpdateOrganizationProfileRequest request) {
        Organization organization = loadAuthorizedOrganization(actor, true);
        if (request.version() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "version is required for organization updates");
        }
        if (request.version() != organization.getVersion()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The organization was changed by another request. Refresh and retry");
        }
        if (request.name() == null && request.email() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "At least one organization profile field is required");
        }

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Organization name cannot be blank");
            }
            organization.setName(name);
        }
        if (request.email() != null) {
            String email = trimToNull(request.email());
            if (email != null && organizationRepository
                    .findByEmailIgnoreCaseAndIdNot(email, organization.getId()).isPresent()) {
                throw new ApiException(ErrorCode.CONFLICT, "An organization with this email already exists");
            }
            organization.setEmail(email == null ? null : email.toLowerCase(Locale.ROOT));
        }
        return OrganizationResponse.from(organizationRepository.saveAndFlush(organization));
    }

    private Organization loadAuthorizedOrganization(AuthenticatedUser actor, boolean adminRequired) {
        if (actor == null || actor.organizationId() == null
                || actor.organizationType() == null
                || (adminRequired && actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        UUID organizationId = actor.organizationId();
        Organization organization = (adminRequired
                ? organizationRepository.findByIdForUpdate(organizationId)
                : organizationRepository.findById(organizationId))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND));
        Organizationtype roleType = actor.role().organizationtype();
        if (roleType == null || organization.getType() != roleType) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return organization;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
