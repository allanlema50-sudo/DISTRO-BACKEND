package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.entity.Warehouse;
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
import com.example.distrobackend.repository.WarehouseRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class OrganizationAdministrationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;

    @Autowired
    public OrganizationAdministrationService(OrganizationRepository organizationRepository,
                                             UserRepository userRepository,
                                             WarehouseRepository warehouseRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.warehouseRepository = warehouseRepository;
    }

    /** Retained for source compatibility with focused service tests. */
    public OrganizationAdministrationService(OrganizationRepository organizationRepository) {
        this(organizationRepository, null, null);
    }

    public OrganizationAdministrationService(OrganizationRepository organizationRepository,
                                             UserRepository userRepository) {
        this(organizationRepository, userRepository, null);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationResponse> list(AuthenticatedUser actor, Pageable pageable) {
        requirePlatformAdmin(actor);
        return organizationRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationResponse> list(AuthenticatedUser actor, Organizationtype type, Pageable pageable) {
        requirePlatformAdmin(actor);
        return organizationRepository.findAllByType(type, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(AuthenticatedUser actor, UUID organizationId) {
        requirePlatformAdmin(actor);
        return organizationRepository.findById(organizationId)
                .map(this::toResponse)
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
        return toResponse(organizationRepository.save(organization));
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
        return toResponse(organizationRepository.save(organization));
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
        return toResponse(organizationRepository.saveAndFlush(organization));
    }

    @Transactional
    public OrganizationResponse updateStatus(AuthenticatedUser actor, Organizationtype type, UUID id,
                                              UpdateOrganizationStatusRequest request) {
        requirePlatformAdmin(actor);
        Organization organization = findOfType(type, id);
        organization.setStatus(request.status());
        return toResponse(organizationRepository.saveAndFlush(organization));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> users(AuthenticatedUser actor, Organizationtype type, UUID id,
                                    Pageable pageable) {
        requirePlatformAdmin(actor);
        findOfType(type, id);
        return userRepository.findByOrganization_Id(id, pageable).map(UserResponse::from);
    }

    private OrganizationResponse toResponse(Organization organization) {
        long userCount = userRepository == null ? 0L : userRepository.countByOrganization_Id(organization.getId());
        String contactName = resolveContactName(organization.getId());
        String location = resolveLocation(organization);
        return OrganizationResponse.from(organization, contactName, location, userCount);
    }

    private String resolveContactName(UUID organizationId) {
        if (userRepository == null) {
            return null;
        }
        return userRepository.findByOrganization_Id(organizationId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(User::getFullName)
                .orElse(null);
    }

    private String resolveLocation(Organization organization) {
        if (warehouseRepository == null) {
            return null;
        }
        if (organization.getDefaultWarehouseId() != null) {
            String fromDefault = warehouseRepository.findById(organization.getDefaultWarehouseId())
                    .map(this::formatWarehouseLocation)
                    .orElse(null);
            if (fromDefault != null && !fromDefault.isBlank()) {
                return fromDefault;
            }
        }
        return warehouseRepository.findByOrganization_Id(organization.getId(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::formatWarehouseLocation)
                .orElse(null);
    }

    private String formatWarehouseLocation(Warehouse warehouse) {
        String city = warehouse.getCity() == null ? "" : warehouse.getCity().trim();
        String address = warehouse.getAddress() == null ? "" : warehouse.getAddress().trim();
        if (!city.isEmpty() && !address.isEmpty()) {
            return city + ", " + address;
        }
        if (!city.isEmpty()) {
            return city;
        }
        if (!address.isEmpty()) {
            return address;
        }
        String description = warehouse.getLocationDescription() == null
                ? "" : warehouse.getLocationDescription().trim();
        if (!description.isEmpty()) {
            return description;
        }
        return warehouse.getName();
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
