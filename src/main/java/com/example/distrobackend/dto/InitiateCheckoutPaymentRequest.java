package com.example.distrobackend.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record InitiateCheckoutPaymentRequest(@NotBlank String method, @Size(max=120) String idempotencyKey) {}
