package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.StopStatus;
import jakarta.validation.constraints.NotNull;

public record TripStopStatusUpdate(@NotNull StopStatus status) {}
