package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ManufacturerShipmentTripRequest(
        @NotNull UUID vehicleId,
        @NotNull UUID driverId,
        @NotEmpty @Size(max = 50) List<UUID> manufacturerShipmentGroupIds,
        @Size(max = 100) String plannedStart,
        @Size(max = 2000) String notes) { }
