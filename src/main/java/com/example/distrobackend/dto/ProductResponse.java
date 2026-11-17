package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.Organizationtype;
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
        String manufacturerName,
        UUID warehouseId,
        int availableQuantity,
        boolean inStock,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProductResponse from(StockItem item) {
        Organization manufacturer = manufacturer(item);
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
                manufacturer == null ? null : manufacturer.getId(),
                manufacturer == null ? null : manufacturer.getName(),
                item.getWarehouse() == null ? null : item.getWarehouse().getId(),
                item.getAvailableQuantity(),
                item.getAvailableQuantity() > 0,
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    public static ProductResponse from(StockItemResponse item) {
        return new ProductResponse(
                item.id(), item.organizationId(), item.sku(), item.name(), item.category(),
                item.brand(), item.unit(), item.description(), item.unitPrice(), item.active(),
                item.publicationStatus(), item.imageUrl(), item.sourceStockItemId(),
                item.manufacturerOrganizationId(), null, item.warehouseId(), item.availableQuantity(),
                item.availableQuantity() > 0, item.createdAt(), item.updatedAt());
    }

    private static Organization manufacturer(StockItem item) {
        if (item.getSourceStockItem() != null) return item.getSourceStockItem().getOrganization();
        return item.getOrganization() != null && item.getOrganization().getType() == Organizationtype.MANUFACTURER
                ? item.getOrganization() : null;
    }

    private static boolean hasImage(StockItem item) {
        return item.isImageAvailable()
                || (item.getSourceStockItem() != null && item.getSourceStockItem().isImageAvailable());
    }
}
