package com.example.distrobackend.dto;

import java.util.List;
import java.util.UUID;

public record ShipmentConsolidationSuggestion(
        UUID originWarehouseId,
        UUID destinationWarehouseId,
        String originAddress,
        String destinationAddress,
        Double destinationLatitude,
        Double destinationLongitude,
        String routePlanningStatus,
        List<UUID> purchaseOrderShipmentIds) {
}
