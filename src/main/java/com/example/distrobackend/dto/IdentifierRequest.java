package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;

public record IdentifierRequest(@NotBlank String identifier) {
}
