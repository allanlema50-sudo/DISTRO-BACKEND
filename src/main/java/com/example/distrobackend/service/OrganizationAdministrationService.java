package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreateOrganizationRequest;
import com.example.distrobackend.dto.OrganizationResponse;
import com.example.distrobackend.dto.UpdateOrganizationProfileRequest;
import com.example.distrobackend.dto.UpdateOrganizationStatusRequest;
import com.example.distrobackend.dto.UserResponse;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class OrganizationAdministrationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Autowired
    public OrganizationAdministrationService(OrganizationRepository organizationRepository,
                                             UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    /** Retained for source compatibility with focused service tests. */
    public OrganizationAdministrationService(OrganizationRepository organizationRepository) {
        this(organizationRepository, null);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationResponse> list(AuthenticatedUser actor, Pageable pageable) {
        requirePlatformAdmin(actor);
        return organizationRepository.findAll(pageable).map(OrganizationResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationResponse> list(AuthenticatedUser actor, Organizationtype type, Pageable pageable) {
        requirePlatformAdmin(actor);
        return organizationRepository.findAllByType(type, pageable).map(OrganizationResponse::from);
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

    @Transactional
    public OrganizationResponse create(AuthenticatedUser actor, Organizationtype type,
                                       CreateOrganizationRequest request) {
        requirePlatformAdmin(actor);
        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setEmail(request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT));
        organization.setType(type);
        return OrganizationResponse.from(organizationRepository.save(organization));
    }

    @Transactional
    public OrganizationResponse update(AuthenticatedUser actor, Organizationtype type, UUID id,
                                       UpdateOrganizationProfileRequest request) {
        requirePlatformAdmin(actor);
        Organization organization = findOfType(type, id);
        if (request.version() != organization.getVersion()) {
            throw new ApiException(ErrorCode.CONFLICT, "Organization has changed; reload and retry");
        }
        if (request.name() == null && request.email() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "At least one organization field is required");
        }
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) throw new ApiException(ErrorCode.BAD_REQUEST, "Organization name cannot be blank");
            organization.setName(name);
        }
        if (request.email() != null) organization.setEmail(request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT));
        return OrganizationResponse.from(organizationRepository.saveAndFlush(organization));
    }

    @Transactional
    public OrganizationResponse updateStatus(AuthenticatedUser actor, Organizationtype type, UUID id,
                                              UpdateOrganizationStatusRequest request) {
        requirePlatformAdmin(actor);
        Organization organization = findOfType(type, id);
        organization.setStatus(request.status());
        return OrganizationResponse.from(organizationRepository.saveAndFlush(organization));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> users(AuthenticatedUser actor, Organizationtype type, UUID id,
                                    Pageable pageable) {
        requirePlatformAdmin(actor);
        findOfType(type, id);
        return userRepository.findByOrganization_Id(id, pageable).map(UserResponse::from);
    }

    private Organization findOfType(Organizationtype type, UUID id) {
        return organizationRepository.findById(id)
                .filter(organization -> organization.getType() == type)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        type.name().toLowerCase(Locale.ROOT) + " not found"));
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
