package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.StockItem;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockItemResponse(
        UUID id,
        UUID organizationId,
        UUID sourceStockItemId,
        UUID manufacturerOrganizationId,
        UUID warehouseId,
        String sku,
        String name,
        String category,
        BigDecimal unitPrice,
        int quantityOnHand,
        int reservedQuantity,
        int availableQuantity,
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
                item.getSourceStockItem() == null ? null : item.getSourceStockItem().getId(),
                item.getSourceStockItem() == null || item.getSourceStockItem().getOrganization() == null
                        ? null : item.getSourceStockItem().getOrganization().getId(),
                item.getWarehouse() == null ? null : item.getWarehouse().getId(),
                item.getSku(),
                item.getName(),
                item.getCategory(),
                item.getUnitPrice(),
                item.getQuantityOnHand(),
                item.getReservedQuantity(),
                item.getAvailableQuantity(),
                item.getReorderThreshold(),
                item.isActive(),
                item.getAvailableQuantity() <= item.getReorderThreshold(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
