package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TripAssignRequest(
        @NotNull UUID riderId,
        UUID vehicleId,
        boolean clearVehicle
) {
    public TripAssignRequest(UUID riderId) {
        this(riderId, null, false);
    }

    public TripAssignRequest(UUID riderId, UUID vehicleId) {
        this(riderId, vehicleId, false);
    }
}
