package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String purchaseOrderNumber,
        UUID buyerOrganizationId,
        UUID supplierOrganizationId,
        PurchaseOrderStatus status,
        BigDecimal totalAmount,
        String currency,
        String rejectionReason,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        OffsetDateTime fulfilledAt,
        List<PurchaseOrderItemResponse> items,
        List<PurchaseOrderSettlementResponse> settlements
) {}
