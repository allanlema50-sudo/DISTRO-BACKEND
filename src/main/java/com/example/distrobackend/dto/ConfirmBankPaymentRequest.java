package com.example.distrobackend.dto;

import java.math.BigDecimal;

public record ConfirmBankPaymentRequest(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String reference,
        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.DecimalMin("0.01")
        @jakarta.validation.constraints.Digits(integer = 10, fraction = 2)
        @com.fasterxml.jackson.annotation.JsonAlias("amount_received") BigDecimal amountReceived
) {}
