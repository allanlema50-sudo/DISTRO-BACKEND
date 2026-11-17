package com.example.distrobackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateStockAuditRequest(
        @NotNull UUID warehouseId,
        @NotNull UUID stockItemId,
        @Min(0) int countedQuantity,
        @Size(max = 2000) String note
) {
}
