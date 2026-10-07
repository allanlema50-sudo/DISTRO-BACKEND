package com.example.distrobackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateStockItemRequest(
        @Size(max = 150)
        @Pattern(regexp = "(?s).*\\S.*", message = "name must contain a non-whitespace character")
        String name,
        @Size(max = 80) String category,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
        @Min(0) Integer reorderThreshold,
        Boolean active
) {
}
