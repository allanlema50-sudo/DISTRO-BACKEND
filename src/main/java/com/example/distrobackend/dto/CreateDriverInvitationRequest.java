package com.example.distrobackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateDriverInvitationRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 20) String phoneNumber,
        @NotBlank @Size(max = 50) String licenseNumber,
        @Size(max = 50) String nationalId,
        @Future LocalDate licenseExpiryDate,
        @JsonAlias("baseWarehouseId") UUID warehouseId,
        @Size(max = 2000) String notes
) {
    public CreateDriverInvitationRequest(String fullName, String email, String phoneNumber, String licenseNumber) {
        this(fullName, email, phoneNumber, licenseNumber, null, null, null, null);
    }
}