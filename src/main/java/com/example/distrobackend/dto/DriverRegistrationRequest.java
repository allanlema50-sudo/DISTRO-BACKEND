package com.example.distrobackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record DriverRegistrationRequest(
        @NotNull @JsonAlias("distributorId") UUID organizationId,
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 20) String phoneNumber,
        @NotBlank @Size(max = 50) @JsonAlias({"nationalIdNumber", "identityNumber"}) String nationalId,
        @NotBlank @Size(max = 50) String licenseNumber,
        @NotNull @Future @JsonAlias("licenseExpiry") LocalDate licenseExpiryDate,
        @Size(max = 2000) @JsonAlias("internalNotes") String notes) {}
