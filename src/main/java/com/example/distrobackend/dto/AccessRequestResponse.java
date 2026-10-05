package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccessRequestResponse(UUID id, String fullName, String phoneNumber, String personalEmail,
                                    String organizationEmail, String organizationName,
                                    Organizationtype organizationType, AccessRequestStatus status,
                                    OffsetDateTime createdAt) {
    public static AccessRequestResponse from(AccessRequest request) {
        return new AccessRequestResponse(request.getId(), request.getFullName(), request.getPhoneNumber(),
                request.getPersonalEmail(), request.getOrganizationEmail(), request.getOrganizationName(),
                request.getOrganizationType(), request.getStatus(), request.getCreatedAt());
    }
}
