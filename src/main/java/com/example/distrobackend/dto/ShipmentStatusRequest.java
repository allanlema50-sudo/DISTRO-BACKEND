package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShipmentStatusRequest(
        @NotNull ShipmentStatus status,
        @Size(max = 2000) String note
) {
}
