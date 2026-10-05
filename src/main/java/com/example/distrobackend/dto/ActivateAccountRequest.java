package com.example.distrobackend.dto;

import com.example.distrobackend.Util.PasswordRules;
import jakarta.validation.constraints.*;

public record ActivateAccountRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 256) String token,
        @NotBlank @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE) String password,
        @NotBlank @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE) String confirmPassword
) {}
