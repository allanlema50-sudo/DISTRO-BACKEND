package com.example.distrobackend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID stockItemId,
        int quantity,
        int pickedQuantity,
        int packedQuantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
