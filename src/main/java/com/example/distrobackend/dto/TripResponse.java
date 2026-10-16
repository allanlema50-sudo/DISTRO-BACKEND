package com.example.distrobackend.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TripResponse(
        UUID id,
        String orderId,
        String customerName,
        String customerPhone,
        String deliveryAddress,
        String driverName,
        UUID driverId,
        String vehicleNumber,
        String status,
        OffsetDateTime estimatedDelivery,
        OffsetDateTime deliveredAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String trackingStatus,
        String currentLocation
) {
}
