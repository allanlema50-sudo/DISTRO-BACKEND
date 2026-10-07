package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PaymentInitiateRequest(
        @NotNull UUID orderId,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "(?:0|254|\\+254)7\\d{8}",
                message = "phoneNumber must be a valid Kenyan mobile number")
        String phoneNumber,
        @Size(max = 100) String idempotencyKey
) {}
