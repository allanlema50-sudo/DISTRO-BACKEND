package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.SharedLoad;
import com.example.distrobackend.Domain.enums.SharedLoadStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SharedLoadResponse(UUID id, UUID proposedTripId, UUID candidateTripId,
        UUID proposerOrganizationId, UUID candidateOrganizationId, SharedLoadStatus status,
        String proposalNote, String reviewNote, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static SharedLoadResponse from(SharedLoad s) {
        return new SharedLoadResponse(s.getId(), s.getProposedTrip().getId(), s.getCandidateTrip().getId(),
                s.getProposerOrganization().getId(), s.getCandidateOrganization().getId(), s.getStatus(),
                s.getProposalNote(), s.getReviewNote(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
