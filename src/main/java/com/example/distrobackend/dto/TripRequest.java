package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.TripType;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record TripRequest(
        @NotNull TripType tripType,
        @NotNull TripLocation origin,
        String scheduledStartLabel,
        Integer totalDistanceKm,
        List<TripStopRequest> stops
) {
    public record TripStopRequest(
            Integer sequence,
            TripLocation location,
            String etaLabel,
            Integer distanceFromPreviousKm,
            UUID orderId, // for DELIVERY
            UUID stockItemId, // for RESTOCK
            Integer restockQuantity,
            String contactName,
            String contactPhone
    ) {}
}
