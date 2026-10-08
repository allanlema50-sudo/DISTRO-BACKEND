package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        UUID customerId,
        UUID organizationId,
        OrderStatus status,
        String deliveryAddress,
        Double deliveryLat,
        Double deliveryLng,
        BigDecimal subtotalAmount,
        BigDecimal deliveryFee,
        BigDecimal totalAmount,
        String cancellationReason,
        OffsetDateTime placedAt,
        OffsetDateTime reservationExpiresAt,
        OffsetDateTime confirmedAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime cancelledAt,
        List<OrderItemResponse> items
) {}
