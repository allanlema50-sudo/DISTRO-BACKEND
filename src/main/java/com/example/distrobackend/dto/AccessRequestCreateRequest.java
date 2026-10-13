package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.Organizationtype;
import jakarta.validation.constraints.*;

public record AccessRequestCreateRequest(
        @NotNull Organizationtype organizationType,
        @NotBlank @Size(max = 150) String organizationName,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Size(max = 20) String phoneNumber,
        @NotBlank @Email @Size(max = 150) String personalEmail,
        @NotBlank @Email @Size(max = 150) String organizationEmail
) {}
