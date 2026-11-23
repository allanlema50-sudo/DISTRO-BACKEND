package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateShipmentConsolidationRequest(
        @NotEmpty @Size(min = 2, max = 50) List<UUID> purchaseOrderShipmentIds) {
}
