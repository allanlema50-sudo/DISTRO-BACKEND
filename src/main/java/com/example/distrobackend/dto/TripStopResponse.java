package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.StopStatus;

import java.util.List;
import java.util.UUID;

public record TripStopResponse(
        String id,
        Integer sequence,
        TripLocation location,
        StopStatus status,
        String etaLabel,
        Integer distanceFromPreviousKm,
        String orderNumber,
        String contactName,
        String contactPhone,
        UUID manufacturerShipmentGroupId,
        String manufacturerShipmentReference,
        List<String> purchaseOrderNumbers
) {
}
