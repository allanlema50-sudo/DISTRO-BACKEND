package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Minimal public response; business identifiers remain visible only to reviewers. */
public record AccessRequestSubmissionResponse(
        UUID id,
        AccessRequestStatus status,
        OffsetDateTime createdAt
) {
    public static AccessRequestSubmissionResponse from(AccessRequest request) {
        return new AccessRequestSubmissionResponse(request.getId(), request.getStatus(), request.getCreatedAt());
    }
}
