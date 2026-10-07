package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.StockMovementType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID stockItemId,
        StockMovementType movementType,
        int quantityDelta,
        String referenceType,
        UUID referenceId,
        UUID performedBy,
        String note,
        OffsetDateTime createdAt
) {}
