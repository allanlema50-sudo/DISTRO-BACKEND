package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.TripType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TripRequest(
        @NotNull TripType tripType,
        @NotNull @Valid TripLocation origin,
        @Size(max = 100) String scheduledStartLabel,
        @Min(0)
        Integer totalDistanceKm,
        @NotEmpty @Size(max = 100) List<@Valid TripStopRequest> stops
) {
    public record TripStopRequest(
            @NotNull @Min(1) Integer sequence,
            @NotNull @Valid TripLocation location,
            @Size(max = 100)
            String etaLabel,
            @Min(0)
            Integer distanceFromPreviousKm,
            UUID orderId, // for DELIVERY
            UUID stockItemId, // for RESTOCK
            @Min(1) Integer restockQuantity,
            @Size(max = 150)
            String contactName,
            @Size(max = 30)
            String contactPhone
    ) {}
}
