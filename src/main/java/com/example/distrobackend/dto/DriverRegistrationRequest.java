package com.example.distrobackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DriverRegistrationRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 20) @JsonAlias({"phone", "mobile", "mobileNumber", "phone_number"}) String phoneNumber,
        @NotBlank @Size(max = 50) @JsonAlias({"nationalIdNumber", "identityNumber", "national_id"}) String nationalId,
        @NotBlank @Size(max = 50) @JsonAlias({"drivingLicenseNumber", "license_number", "driving_license_number"}) String licenseNumber,
        @NotNull @Future @JsonAlias({"licenseExpiry", "expiryDate", "license_expiry_date", "license_expiry"}) LocalDate licenseExpiryDate,
        @Size(max = 2000) @JsonAlias("internalNotes") String notes) {}
