package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.StockItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockItemResponse(
        UUID id,
        UUID organizationId,
        String sku,
        String name,
        String category,
        BigDecimal unitPrice,
        int quantityOnHand,
        int reorderThreshold,
        boolean active,
        boolean lowStock,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static StockItemResponse from(StockItem item) {
        return new StockItemResponse(
                item.getId(),
                item.getOrganization() == null ? null : item.getOrganization().getId(),
                item.getSku(),
                item.getName(),
                item.getCategory(),
                item.getUnitPrice(),
                item.getQuantityOnHand(),
                item.getReorderThreshold(),
                item.isActive(),
                item.getQuantityOnHand() <= item.getReorderThreshold(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
