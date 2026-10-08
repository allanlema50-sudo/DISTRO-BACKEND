package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.StockMovement;
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
) {
    public static StockMovementResponse from(StockMovement movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getStockItem().getId(),
                movement.getMovementType(),
                movement.getQuantityDelta(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getPerformedBy() == null ? null : movement.getPerformedBy().getId(),
                movement.getNote(),
                movement.getCreatedAt());
    }
}
