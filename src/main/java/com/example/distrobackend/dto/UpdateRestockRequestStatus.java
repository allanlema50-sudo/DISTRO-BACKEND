package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.RestockRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRestockRequestStatus(
        @NotNull RestockRequestStatus status,
        @Size(max = 2000) String note
) {
}
