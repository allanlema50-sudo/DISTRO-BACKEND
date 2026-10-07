package com.example.distrobackend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PurchaseOrderItemRequest(
        @NotNull UUID stockItemId,
        @Min(1) int quantity
) {}
