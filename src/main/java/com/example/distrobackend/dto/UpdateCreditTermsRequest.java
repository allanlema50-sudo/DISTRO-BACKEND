package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.CreditTermStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record UpdateCreditTermsRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal creditLimit,
        @NotNull @Min(1) @Max(365) Integer termDays,
        CreditTermStatus status,
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a three-letter ISO currency code") String currency,
        Long expectedVersion
) {}
