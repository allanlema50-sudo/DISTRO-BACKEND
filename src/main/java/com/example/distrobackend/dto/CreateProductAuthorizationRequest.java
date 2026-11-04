package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateProductAuthorizationRequest(
        @NotNull UUID manufacturerProductId,
        @Size(max = 1000) String requestNote) {
    public CreateProductAuthorizationRequest(UUID manufacturerProductId) {
        this(manufacturerProductId, null);
    }
}
