package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DemoDispatchRequest(
        @NotNull UUID driverId,
        @NotNull UUID vehicleId
) {
}
