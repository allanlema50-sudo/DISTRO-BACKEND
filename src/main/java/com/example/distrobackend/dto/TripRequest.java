package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.TripType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;

import java.util.List;
import java.util.UUID;

public record TripRequest(
        @NotNull TripType tripType,
        @NotNull TripLocation origin,
        String scheduledStartLabel,
        Integer totalDistanceKm,
        @NotEmpty @Valid List<TripStopRequest> stops
) {
    public record TripStopRequest(
            @NotNull @Min(1) Integer sequence,
            @NotNull @Valid TripLocation location,
            String etaLabel,
            Integer distanceFromPreviousKm,
            UUID orderId, // for DELIVERY
            UUID stockItemId, // for RESTOCK
            @Min(1) Integer restockQuantity,
            String contactName,
            String contactPhone
    ) {}
}
