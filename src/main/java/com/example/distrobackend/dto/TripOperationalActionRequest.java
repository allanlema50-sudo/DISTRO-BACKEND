package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.TripOperationalActionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripOperationalActionRequest(
        @NotNull TripOperationalActionType action,
        @Size(max = 1000) String note
) {
}
