package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(@NotBlank String identifier,
                               @NotBlank @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits") String otp) {
}
