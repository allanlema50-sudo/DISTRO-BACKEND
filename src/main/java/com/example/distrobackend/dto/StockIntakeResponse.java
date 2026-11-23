package com.example.distrobackend.dto;

import java.util.UUID;

public record StockIntakeResponse(
        UUID movementId,
        UUID stockItemId,
        UUID warehouseId,
        int quantityReceived,
        int quantityOnHand,
        int reservedQuantity,
        int availableQuantity,
        String sourceReference,
        String status) {
}
