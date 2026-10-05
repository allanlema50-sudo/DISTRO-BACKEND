package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Invitation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationResponse(

        UUID id,
        String fullName,
        String email,
        String phoneNumber,
        String role,
        String status,
        String invitationLink,
        OffsetDateTime expiresAt

) {

    public static InvitationResponse from(
            Invitation invitation,
            String invitationLink
    ) {

        return new InvitationResponse(
                invitation.getId(),
                invitation.getFullName(),
                invitation.getEmail(),
                invitation.getPhoneNumber(),
                invitation.getRole().name(),
                invitation.getStatus().name(),
                invitationLink,
                invitation.getExpiresAt()
        );
    }
}