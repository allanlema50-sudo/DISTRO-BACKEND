package com.example.distrobackend.dto.organization;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.Organizationtype;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationResponse(

        UUID id,
        String name,
        Organizationtype type,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt

) {

    public static OrganizationResponse from(
            Organization organization
    ) {

        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getType(),
                organization.getCreatedAt(),
                organization.getUpdatedAt()
        );
    }
}