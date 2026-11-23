package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.Organizationtype;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateCommissionRuleRequest(
        @NotNull @DecimalMin("0.0000") @DecimalMax("100.0000") @Digits(integer = 3, fraction = 4)
        BigDecimal ratePercent,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal fixedFee,
        @NotBlank @Size(min = 3, max = 3) String currency,
        Organizationtype organizationType,
        @NotNull OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveUntil
) {}
