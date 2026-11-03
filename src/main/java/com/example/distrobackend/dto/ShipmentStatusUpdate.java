package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.ShipmentStatus;
import jakarta.validation.constraints.NotNull;

public record ShipmentStatusUpdate(@NotNull ShipmentStatus status, String note) {}
