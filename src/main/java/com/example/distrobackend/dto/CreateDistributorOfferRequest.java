package com.example.distrobackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateDistributorOfferRequest(
        @NotNull UUID sourceStockItemId,
        @NotNull UUID warehouseId,
        @NotBlank @Size(max = 50) String sku,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal sellingPrice,
        @Min(0) int reorderThreshold
) {}
