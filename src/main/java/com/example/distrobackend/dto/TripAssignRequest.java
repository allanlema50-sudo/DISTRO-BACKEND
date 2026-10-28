package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TripAssignRequest(
        @NotNull UUID riderId,
        UUID vehicleId
) {
    public TripAssignRequest(UUID riderId) {
        this(riderId, null);
    }
}
