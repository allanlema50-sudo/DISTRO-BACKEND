package com.example.distrobackend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        String customerName,
        String customerEmail,
        UUID organizationId,
        String organizationName,
        BigDecimal totalAmount,
        int itemCount,
        String status,
        String paymentStatus,
        String deliveryStatus,
        String deliveryAddress,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}