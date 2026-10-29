package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.StockAudit;
import com.example.distrobackend.Domain.enums.StockAuditStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StockAuditResponse(
        UUID id,
        UUID organizationId,
        UUID warehouseId,
        UUID stockItemId,
        int systemQuantity,
        int countedQuantity,
        int variance,
        StockAuditStatus status,
        String note,
        UUID recordedBy,
        UUID appliedBy,
        OffsetDateTime appliedAt,
        OffsetDateTime createdAt
) {
    public static StockAuditResponse from(StockAudit audit) {
        return new StockAuditResponse(
                audit.getId(),
                audit.getOrganization().getId(),
                audit.getWarehouse().getId(),
                audit.getStockItem().getId(),
                audit.getSystemQuantity(),
                audit.getCountedQuantity(),
                audit.getVariance(),
                audit.getStatus(),
                audit.getNote(),
                audit.getRecordedBy().getId(),
                audit.getAppliedBy() == null ? null : audit.getAppliedBy().getId(),
                audit.getAppliedAt(),
                audit.getCreatedAt());
    }
}
