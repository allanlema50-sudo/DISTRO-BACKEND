package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmStopRequest(
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits")
        String otp
) {
}
