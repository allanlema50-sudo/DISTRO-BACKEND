package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.SettlementBatch;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SettlementBatchResponse(
        UUID id, String provider, String status, String currency, BigDecimal totalAmount,
        int settlementCount, UUID createdBy, UUID approvedBy, OffsetDateTime approvedAt,
        OffsetDateTime executedAt, String note, OffsetDateTime createdAt
) {
    public static SettlementBatchResponse from(SettlementBatch batch) {
        return new SettlementBatchResponse(
                batch.getId(), batch.getProvider().name(), batch.getStatus().name(), batch.getCurrency(),
                batch.getTotalAmount(), batch.getSettlementCount(),
                batch.getCreatedBy().getId(), batch.getApprovedBy() == null ? null : batch.getApprovedBy().getId(),
                batch.getApprovedAt(), batch.getExecutedAt(), batch.getNote(), batch.getCreatedAt());
    }
}
