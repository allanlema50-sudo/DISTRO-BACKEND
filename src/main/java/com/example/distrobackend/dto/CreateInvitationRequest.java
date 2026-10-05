package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(

        @NotBlank
        @Size(max = 150)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @Size(max = 20)
        String phoneNumber,

        @NotNull
        UserRole role

) {}