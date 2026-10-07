package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdate(
        @NotNull OrderStatus status,
        String note
) {}
