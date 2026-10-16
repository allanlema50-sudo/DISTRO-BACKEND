package com.example.distrobackend.dto;

import java.math.BigDecimal;

public record OrderStatsResponse(
        long totalOrders,
        long activeOrders,
        long pendingOrders,
        BigDecimal totalOrderValue
) {
}