package com.example.distrobackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Mutable organization profile fields; organization type is immutable. */
public record UpdateOrganizationProfileRequest(
        @Size(max = 150) String name,
        @Email @Size(max = 150) String email,
        Long version,
        @Size(max = 150) String registeredName,
        @Size(max = 100) String taxRegistrationId,
        @Size(max = 80) String timezone,
        java.util.UUID defaultWarehouseId
) {
    public UpdateOrganizationProfileRequest(String name, String email, Long version) {
        this(name, email, version, null, null, null, null);
    }
}
