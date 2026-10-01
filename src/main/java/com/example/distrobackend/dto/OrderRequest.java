package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record OrderRequest(
        @NotNull UUID organizationId,
        String deliveryAddress,
        Double deliveryLat,
        Double deliveryLng,
        @NotEmpty List<OrderItemRequest> items
) {}
