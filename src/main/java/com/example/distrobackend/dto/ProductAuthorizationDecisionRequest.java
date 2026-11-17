package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.ProductAuthorizationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

public record ProductAuthorizationDecisionRequest(
        @NotNull ProductAuthorizationStatus status,
        @Size(max = 1000) String note,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal agreedWholesalePrice,
        @Min(1) Integer minimumOrderQuantity
) {
    public ProductAuthorizationDecisionRequest(ProductAuthorizationStatus status, String note) {
        this(status, note, null, null);
    }
}
