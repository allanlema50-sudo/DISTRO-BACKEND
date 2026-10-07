package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PaymentInitiateRequest(
        @NotNull UUID orderId,
        @NotBlank String phoneNumber,
        @Size(max = 100) String idempotencyKey
) {}
