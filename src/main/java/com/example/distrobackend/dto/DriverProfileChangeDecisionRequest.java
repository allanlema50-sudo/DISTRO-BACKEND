package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;

public record DriverProfileChangeDecisionRequest(@NotBlank String decision) {
}
