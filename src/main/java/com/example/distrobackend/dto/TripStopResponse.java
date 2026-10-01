package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.StopStatus;

public record TripStopResponse(
        String id,
        Integer sequence,
        TripLocation location,
        StopStatus status,
        String etaLabel,
        Integer distanceFromPreviousKm,
        String orderNumber,
        String contactName,
        String contactPhone
) {
}
