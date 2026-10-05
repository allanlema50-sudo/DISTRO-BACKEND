package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

public record OrderRequest(
        @NotNull UUID organizationId,
        String deliveryAddress,
        Double deliveryLat,
        Double deliveryLng,
        @Valid @NotEmpty List<OrderItemRequest> items
) {}
