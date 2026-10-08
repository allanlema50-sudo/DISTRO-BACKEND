package com.example.distrobackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.example.distrobackend.Domain.enums.Organizationtype;
import jakarta.validation.constraints.*;

public record AccessRequestCreateRequest(
        @JsonAlias("organization_type")
        @NotNull Organizationtype organizationType,
        @JsonAlias("organization_name")
        @NotBlank @Size(max = 150) String organizationName,
        @JsonAlias("full_name")
        @NotBlank @Size(max = 150) String fullName,
        @JsonAlias("phone_number")
        @NotBlank @Size(max = 20) String phoneNumber,
        @JsonAlias({"personal_email", "email"})
        @NotBlank @Email @Size(max = 150) String personalEmail,
        @JsonAlias("organization_email")
        @NotBlank @Email @Size(max = 150) String organizationEmail
) {}
