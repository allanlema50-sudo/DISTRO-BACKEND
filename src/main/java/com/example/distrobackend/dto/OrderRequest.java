package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

public record OrderRequest(
        UUID organizationId,
        String deliveryAddress,
        Double deliveryLat,
        Double deliveryLng,
        @NotEmpty List<@Valid OrderItemRequest> items
) {}
