package com.example.distrobackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record FreightTripRequest(
        @NotNull UUID vehicleId,
        @NotNull UUID driverId,
        @NotEmpty @Size(max = 50) List<UUID> shipmentIds,
        @Size(max = 100) String plannedStart,
        @Size(max = 100) String plannedEnd,
        @Min(0) Integer distanceKm,
        @Size(max = 2000) String notes
) {
}
