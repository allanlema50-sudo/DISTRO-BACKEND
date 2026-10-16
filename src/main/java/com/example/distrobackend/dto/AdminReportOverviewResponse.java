
package com.example.distrobackend.dto;

import java.math.BigDecimal;

public record AdminReportOverviewResponse(
        long totalOrders,
        long pendingOrders,
        BigDecimal totalRevenue,
        long totalPayments,
        long confirmedPayments,
        long totalDeliveries,
        long completedDeliveries,
        long totalCustomers,
        long totalOrganizations,
        long totalStockItems,
        long lowStockItems,
        long outOfStockItems,
        BigDecimal inventoryValue
) {
}
