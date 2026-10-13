package com.example.distrobackend.dto;




import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Util.PasswordRules;
import jakarta.validation.constraints.*;

/**
 * Public self-registration is limited to CUSTOMER. Organization administrators
 * are created by platform approval; staff are created through organization invitations.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @Email @Size(max = 150) String email,
        @NotBlank String phoneNumber,
        @NotBlank @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE) String password,
        @NotNull UserRole role
) {}
