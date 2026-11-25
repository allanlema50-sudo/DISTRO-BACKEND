package com.example.distrobackend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TripAssignRequest(
        @NotNull
        @JsonAlias("driverId")
        UUID riderId,
        UUID vehicleId,
        Boolean clearVehicle
) {
    public TripAssignRequest {
        if (clearVehicle == null) {
            clearVehicle = false;
        }
    }

    public TripAssignRequest(UUID riderId) {
        this(riderId, null, false);
    }

    public TripAssignRequest(UUID riderId, UUID vehicleId) {
        this(riderId, vehicleId, false);
    }
}
