package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShipmentStatusRequest(
        @NotNull PurchaseOrderShipmentStatus status,
        @Size(max = 2000) String note
) {
}
