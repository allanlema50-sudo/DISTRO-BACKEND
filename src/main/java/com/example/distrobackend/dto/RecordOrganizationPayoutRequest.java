package com.example.distrobackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordOrganizationPayoutRequest(
        @NotNull UUID organizationId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a three-letter ISO currency code")
        String currency,
        @NotBlank @Size(max = 100) String bankReference,
        @Size(max = 120) String idempotencyKey
) {}
