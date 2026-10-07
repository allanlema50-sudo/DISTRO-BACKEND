package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TripAssignRequest(
        @NotNull UUID riderId
) {
}
