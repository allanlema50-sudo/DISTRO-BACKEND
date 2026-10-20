package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentStatusUpdateRequest(
        @NotNull PaymentStatus status,
        @Size(max = 2000) String note
) {}
