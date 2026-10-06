package com.example.distrobackend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminStockResponse(
        UUID id,
        String sku,
        String name,
        String category,
        BigDecimal unitPrice,
        int quantityOnHand,
        int reorderThreshold,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
