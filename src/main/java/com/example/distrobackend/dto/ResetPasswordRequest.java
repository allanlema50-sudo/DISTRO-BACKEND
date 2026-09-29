package com.example.distrobackend.dto;




import com.example.distrobackend.Util.PasswordRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
        @NotBlank String identifier,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits") String otp,
        @NotBlank @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE) String newPassword
) {}