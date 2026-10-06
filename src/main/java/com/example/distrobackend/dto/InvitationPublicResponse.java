package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Invitation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationPublicResponse(

        UUID id,

        String fullName,

        String email,

        String role,

        String organizationName,

        UUID organizationId,

        String status,

        OffsetDateTime expiresAt

) {

    public static InvitationPublicResponse from(
            Invitation invitation
    ) {

        return new InvitationPublicResponse(
                invitation.getId(),
                invitation.getFullName(),
                invitation.getEmail(),
                invitation.getRole().name(),
                invitation.getOrganization().getName(),
                invitation.getOrganization().getId(),
                invitation.getStatus().name(),
                invitation.getExpiresAt()
        );
    }
}