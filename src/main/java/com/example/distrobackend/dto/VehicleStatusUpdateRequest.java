package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.VehicleStatus;
import jakarta.validation.constraints.NotNull;

public record VehicleStatusUpdateRequest(@NotNull VehicleStatus status) {
}
