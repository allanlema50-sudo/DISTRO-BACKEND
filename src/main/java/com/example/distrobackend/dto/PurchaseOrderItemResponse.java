package com.example.distrobackend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
        UUID id,
        UUID stockItemId,
        String sku,
        String name,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
