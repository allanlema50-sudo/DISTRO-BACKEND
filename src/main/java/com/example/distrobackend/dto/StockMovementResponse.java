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
        int movedQuantity,
        String referenceType,
        UUID referenceId,
        UUID performedBy,
        String note,
        OffsetDateTime createdAt
) {
    public static StockMovementResponse from(StockMovement movement) {
        int moved = movement.getLocationQuantityDelta() == null
                ? 0
                : Math.abs(movement.getLocationQuantityDelta());
        return new StockMovementResponse(
                movement.getId(),
                movement.getStockItem().getId(),
                movement.getMovementType(),
                movement.getQuantityDelta(),
                moved,
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getPerformedBy() == null ? null : movement.getPerformedBy().getId(),
                movement.getNote(),
                movement.getCreatedAt());
    }
}
