package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.FeePayer;
import com.example.distrobackend.Domain.enums.PlatformFeeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record UpdatePlatformFeeRequest(
        @NotNull PlatformFeeType feeType,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal feeValue,
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a three-letter ISO currency code") String currency,
        FeePayer feePayer,
        Long expectedVersion
) {}
