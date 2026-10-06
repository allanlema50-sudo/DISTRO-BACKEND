package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.TripStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripStatusUpdate(
        @NotNull TripStatus status,
        @Size(max = 500) String note
) {
}
