package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PutAwayStockRequest(@NotBlank @Size(max = 80) String locationCode,
                                  @Positive int quantity,
                                  @Size(max = 100) String idempotencyKey) {
    public PutAwayStockRequest(String locationCode, int quantity) {
        this(locationCode, quantity, null);
    }
}
