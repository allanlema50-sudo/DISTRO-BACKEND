package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Organization getOrganization(UUID id) {

        return organizationRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Organization not found: " + id
                        )
                );
    }

    @Transactional
    public Organization createOrganization(
            String name,
            Organizationtype type
    ) {

        if (organizationRepository.existsByNameIgnoreCase(name)) {
            throw new RuntimeException(
                    "An organization with this name already exists."
            );
        }

        Organization organization = new Organization();

        organization.setName(name);
        organization.setType(type);

        return organizationRepository.save(organization);
    }

    @Transactional
    public void deactivateOrganization(UUID id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        "Organization not found"
                ));

        organization.setActive(false);
        organizationRepository.save(organization);
    }

    @Transactional
    public Organization setOrganizationActive(UUID id, boolean active) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.NOT_FOUND,
                        "Organization not found"
                ));

        organization.setActive(active);
        return organizationRepository.save(organization);
    }
}