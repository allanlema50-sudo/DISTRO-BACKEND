package com.example.distrobackend.dto;

import java.util.UUID;

public record DemoDispatchResponse(
        UUID orderId,
        String orderNumber,
        String orderStatus,
        UUID tripId,
        String tripNumber,
        UUID stopId,
        String driverName,
        String vehicleRegistration,
        String deliveryOtp,
        String message
) {
}
