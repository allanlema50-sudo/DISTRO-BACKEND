package com.example.distrobackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRestockRequest(
        @NotNull UUID stockItemId,
        @NotNull UUID manufacturerOrganizationId,
        @Min(1) int requestedQuantity,
        @Size(max = 2000) String note
) {
}
