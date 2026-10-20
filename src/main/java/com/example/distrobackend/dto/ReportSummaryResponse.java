package com.example.distrobackend.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ReportSummaryResponse(
        String report,
        long organizations,
        long users,
        long orders,
        long payments,
        long trips,
        long stockItems,
        long lowStockItems,
        BigDecimal amount,
        Map<String, Long> byStatus
) {}
