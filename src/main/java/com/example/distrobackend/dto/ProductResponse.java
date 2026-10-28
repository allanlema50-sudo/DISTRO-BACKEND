package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.ProductPublicationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID organizationId,
        String sku,
        String name,
        String category,
        String brand,
        String unit,
        String description,
        BigDecimal unitPrice,
        boolean active,
        ProductPublicationStatus publicationStatus,
        String imageUrl,
        UUID sourceStockItemId,
        UUID manufacturerOrganizationId,
        UUID warehouseId,
        boolean inStock,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductResponse from(StockItem item) {
        return new ProductResponse(
                item.getId(),
                item.getOrganization() == null ? null : item.getOrganization().getId(),
                item.getSku(),
                item.getName(),
                item.getCategory(),
                item.getBrand(),
                item.getUnit(),
                item.getDescription(),
                item.getUnitPrice(),
                item.isActive(),
                item.getPublicationStatus(),
                hasImage(item) ? "/api/v1/products/" + item.getId() + "/image" : null,
                item.getSourceStockItem() == null ? null : item.getSourceStockItem().getId(),
                item.getSourceStockItem() == null || item.getSourceStockItem().getOrganization() == null
                        ? null : item.getSourceStockItem().getOrganization().getId(),
                item.getWarehouse() == null ? null : item.getWarehouse().getId(),
                item.getAvailableQuantity() > 0,
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    public static ProductResponse from(StockItemResponse item) {
        return new ProductResponse(
                item.id(),
                item.organizationId(),
                item.sku(),
                item.name(),
                item.category(),
                item.brand(),
                item.unit(),
                item.description(),
                item.unitPrice(),
                item.active(),
                item.publicationStatus(),
                item.imageUrl(),
                item.sourceStockItemId(),
                item.manufacturerOrganizationId(),
                item.warehouseId(),
                item.availableQuantity() > 0,
                item.createdAt(),
                item.updatedAt());
    }

    private static boolean hasImage(StockItem item) {
        return item.isImageAvailable()
                || (item.getSourceStockItem() != null && item.getSourceStockItem().isImageAvailable());
    }

}
