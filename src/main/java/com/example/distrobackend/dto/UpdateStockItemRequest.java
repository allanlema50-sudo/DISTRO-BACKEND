package com.example.distrobackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateStockItemRequest(
        @NotBlank @Size(max = 150) String name,
        String category,
        @NotNull @DecimalMin(value = "0.01") BigDecimal unitPrice,
        @Min(0) int reorderThreshold,
        @NotNull Boolean active
) {}
