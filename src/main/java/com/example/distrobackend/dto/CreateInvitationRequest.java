package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateInvitationRequest(

        @NotBlank
        @Size(max = 150)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Size(max = 20)
        String phoneNumber,

        @NotNull
        UserRole role,

        /*
         * Required when SUPER_ADMIN is creating an invitation.
         *
         * Organization admins do not need to provide this because
         * their organization is taken from their authenticated account.
         */
        UUID organizationId

) {
}
