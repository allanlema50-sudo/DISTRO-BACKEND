package com.example.distrobackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateStockItemRequest(
        @NotBlank @Size(max = 50) String sku,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 80) String category,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @Min(0) int initialQuantity,
        @Min(0) int reorderThreshold
) {
}
