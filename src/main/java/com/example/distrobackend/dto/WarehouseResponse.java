package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Warehouse;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        String address,
        String city,
        String postalCode,
        String siteManager,
        boolean active,
        long stockUnits,
        long staffCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {
    public static WarehouseResponse from(Warehouse warehouse, long stockUnits, long staffCount) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getOrganization().getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getCity(),
                warehouse.getPostalCode(),
                warehouse.getSiteManager(),
                warehouse.isActive(),
                stockUnits,
                staffCount,
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt(),
                warehouse.getVersion());
    }
}
