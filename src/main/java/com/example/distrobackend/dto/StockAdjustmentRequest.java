package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.StockMovementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StockAdjustmentRequest(
        int quantityDelta,
        @NotNull StockMovementType movementType,
        @NotBlank String note
) {}
