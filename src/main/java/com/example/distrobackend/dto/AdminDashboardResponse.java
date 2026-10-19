package com.example.distrobackend.dto;

public record AdminDashboardResponse(
        long organizations,
        long users,
        long activeUsers,
        long pendingAccessRequests,
        long orders,
        long payments,
        long trips,
        long stockItems
) {}
