package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.OrganizationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String email,
        Organizationtype type,
        OrganizationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(organization.getId(), organization.getName(),
                organization.getEmail(), organization.getType(), organization.getStatus(), organization.getCreatedAt(),
                organization.getUpdatedAt(), organization.getVersion());
    }
}
