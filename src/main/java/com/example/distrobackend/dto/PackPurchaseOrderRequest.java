package com.example.distrobackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.UUID;

public record PackPurchaseOrderRequest(
        @Positive int packageCount,
        @NotEmpty List<@Valid Line> lines) {
    public record Line(@NotNull UUID purchaseOrderItemId, @PositiveOrZero int packedQuantity) {
    }
}
