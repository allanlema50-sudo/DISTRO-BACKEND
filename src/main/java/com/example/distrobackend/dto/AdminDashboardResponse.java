package com.example.distrobackend.dto;

public record AdminDashboardResponse(
        long totalUsers,
        long totalProducts,
        long totalOrders,
        long pendingOrders,
        long totalPayments,
        long totalTrips,
        long lowStockProducts
) {
}