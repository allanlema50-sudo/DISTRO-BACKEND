package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateStockIntakeRequest(
        @Positive int quantity,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotBlank @Size(max = 120) String sourceReference,
        @Size(max = 1000) String note) {
}
