package com.example.distrobackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PurchaseOrderItemRequest(
        @NotNull UUID stockItemId,
        @Min(1) int quantity,
        UUID distributorOfferId
) {
    /** Backwards-compatible constructor for clients that have not selected an offer. */
    public PurchaseOrderItemRequest(UUID stockItemId, int quantity) {
        this(stockItemId, quantity, null);
    }
}
