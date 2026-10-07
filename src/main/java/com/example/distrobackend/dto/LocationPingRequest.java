package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

public record LocationPingRequest(
        @NotNull Double lat,
        @NotNull Double lng
) {
}
