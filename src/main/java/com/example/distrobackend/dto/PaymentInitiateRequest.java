package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PaymentInitiateRequest(
        @NotNull UUID orderId,
        @NotBlank String phoneNumber // The phone number to push the M-Pesa prompt to
) {}
