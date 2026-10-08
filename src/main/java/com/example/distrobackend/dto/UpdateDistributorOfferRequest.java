package com.example.distrobackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateDistributorOfferRequest(
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal sellingPrice,
        @Min(0) Integer reorderThreshold,
        Boolean active
) {
    public boolean isEmpty() {
        return sellingPrice == null && reorderThreshold == null && active == null;
    }
}
