package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.Organizationtype;
import jakarta.validation.constraints.*;

public record AccessRequestCreateRequest(
        @NotNull Organizationtype organizationType,
        @NotBlank @Size(max = 150) String organizationName,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 100) String registrationNumber,
        @NotBlank @Size(max = 50) String taxIdentifier,
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Pattern(regexp = "Manager|Supervisor",
                message = "representativeRole must be Manager or Supervisor") String representativeRole,
        @NotBlank @Email @Size(max = 150) String personalEmail,
        @NotBlank @Email @Size(max = 150) String organizationEmail,
        @NotBlank @Size(max = 20) String phoneNumber,
        @NotNull @AssertTrue(message = "authorizedRepresentative must be true") Boolean authorizedRepresentative
) {}
