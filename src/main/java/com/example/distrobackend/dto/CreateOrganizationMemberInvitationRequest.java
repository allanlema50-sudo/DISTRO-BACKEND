package com.example.distrobackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateOrganizationMemberInvitationRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 20) String phoneNumber,
        @Size(max = 100) String jobTitle,
        UUID warehouseId
) {}
