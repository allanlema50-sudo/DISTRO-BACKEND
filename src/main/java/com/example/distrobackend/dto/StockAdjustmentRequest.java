package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.StockMovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StockAdjustmentRequest(
        @NotNull StockMovementType movementType,
        @NotNull Integer quantityDelta,
        @Size(max = 50) String referenceType,
        UUID referenceId,
        @Size(max = 2000) String note
) {
}
