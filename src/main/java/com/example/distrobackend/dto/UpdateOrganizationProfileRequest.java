package com.example.distrobackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Mutable organization profile fields; organization type is immutable. */
public record UpdateOrganizationProfileRequest(
        @Size(max = 150) String name,
        @Email @Size(max = 150) String email,
        @NotNull Long version
) {}
