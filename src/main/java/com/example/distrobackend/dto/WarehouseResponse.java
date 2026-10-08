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
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getOrganization().getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.isActive(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt());
    }
}
