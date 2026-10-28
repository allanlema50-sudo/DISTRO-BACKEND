package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateDriverAvailabilityRequest(@NotNull Boolean available) {
}
