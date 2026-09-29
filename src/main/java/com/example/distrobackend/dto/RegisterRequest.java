package com.example.distrobackend.dto;




import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Util.PasswordRules;
import jakarta.validation.constraints.*;

/**
 * Self-registration. Allowed roles: CUSTOMER, MANUFACTURER_ADMIN, DISTRIBUTOR_ADMIN.
 * Staff and drivers are invited by an organization admin instead.
 * For the two admin roles, organizationName and email are required (a new organization is created).
 */
public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @NotBlank String phoneNumber,
        @NotBlank @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE) String password,
        @NotNull UserRole role,
        @Size(max = 150) String organizationName
) {}