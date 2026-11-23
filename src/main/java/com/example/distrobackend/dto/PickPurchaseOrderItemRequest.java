package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record PickPurchaseOrderItemRequest(
        @NotNull UUID purchaseOrderItemId,
        @PositiveOrZero int pickedQuantity) {
}
