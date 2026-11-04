package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateShipmentRequest(
        @NotNull UUID purchaseOrderId,
        @NotNull UUID destinationWarehouseId,
        @Size(max = 100) String trackingNumber,
        @Size(max = 100) String carrier
) {
}
