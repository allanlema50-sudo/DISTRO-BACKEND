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
        @NotEmpty List<@Valid OrderItemRequest> items,
        UUID customerId
) {
    public OrderRequest(
            UUID organizationId,
            String deliveryAddress,
            Double deliveryLat,
            Double deliveryLng,
            List<@Valid OrderItemRequest> items
    ) {
        this(organizationId, deliveryAddress, deliveryLat, deliveryLng, items, null);
    }
}
