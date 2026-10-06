package com.example.distrobackend.dto;

import java.util.UUID;

public record OrganizationDashboardResponse(
        UUID organizationId,
        String organizationName,
        String organizationType,

        long totalUsers,
        long totalProducts,
        long activeProducts,

        long totalStockUnits,
        long lowStockProducts,
        long pendingStockApprovals,

        long totalOrders,
        long pendingOrders,

        long totalPayments,
        long pendingPayments,

        long totalTrips,
        long activeTrips
) {
}
