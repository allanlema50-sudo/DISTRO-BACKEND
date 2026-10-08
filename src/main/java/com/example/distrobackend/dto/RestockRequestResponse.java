package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.RestockRequest;
import com.example.distrobackend.Domain.enums.RestockRequestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RestockRequestResponse(
        UUID id,
        UUID distributorOrganizationId,
        UUID manufacturerOrganizationId,
        UUID stockItemId,
        String stockItemSku,
        int requestedQuantity,
        RestockRequestStatus status,
        UUID requestedBy,
        String note,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static RestockRequestResponse from(RestockRequest request) {
        return new RestockRequestResponse(
                request.getId(),
                request.getDistributorOrganization().getId(),
                request.getManufacturerOrganization().getId(),
                request.getStockItem().getId(),
                request.getStockItem().getSku(),
                request.getRequestedQuantity(),
                request.getStatus(),
                request.getRequestedBy().getId(),
                request.getNote(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
