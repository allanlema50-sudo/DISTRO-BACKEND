package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.StockItem;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID organizationId,
        String sku,
        String name,
        String category,
        BigDecimal unitPrice,
        boolean active
) {
    public static ProductResponse from(StockItem item) {
        return new ProductResponse(
                item.getId(),
                item.getOrganization() == null ? null : item.getOrganization().getId(),
                item.getSku(),
                item.getName(),
                item.getCategory(),
                item.getUnitPrice(),
                item.isActive());
    }

    public static ProductResponse from(StockItemResponse item) {
        return new ProductResponse(
                item.id(),
                item.organizationId(),
                item.sku(),
                item.name(),
                item.category(),
                item.unitPrice(),
                item.active());
    }
}
