package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.SettlementStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseOrderSettlementResponse(
        UUID id,
        SettlementStatus status,
        BigDecimal amount,
        String currency,
        String providerReference,
        String note,
        OffsetDateTime settledAt,
        OffsetDateTime createdAt
) {}
