package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccessRequestResponse(

        UUID id,

        String fullName,

        String email,

        String phoneNumber,

        String organizationName,

        Organizationtype organizationType,

        UserRole requestedRole,

        AccessRequestStatus status,

        OffsetDateTime createdAt,

        OffsetDateTime reviewedAt,

        OffsetDateTime activatedAt

) {

    public static AccessRequestResponse from(
            AccessRequest request
    ) {

        return new AccessRequestResponse(

                request.getId(),

                request.getFullName(),

                request.getEmail(),

                request.getPhoneNumber(),

                request.getOrganizationName(),

                request.getOrganizationType(),

                request.getRequestedRole(),

                request.getStatus(),

                request.getCreatedAt(),

                request.getReviewedAt(),

                request.getActivatedAt()

        );
    }
}