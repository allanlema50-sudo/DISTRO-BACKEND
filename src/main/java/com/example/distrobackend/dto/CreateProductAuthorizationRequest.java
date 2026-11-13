package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateProductAuthorizationRequest(
        @NotNull UUID manufacturerProductId,
        @Size(max = 1000) String requestNote,
        @Size(max = 500) String targetMarket,
        @Size(max = 500) String salesChannels,
        @Size(max = 500) String geographicalCoverage,
        @Size(max = 4000) String distributionStrategy) {
    public CreateProductAuthorizationRequest(UUID manufacturerProductId) {
        this(manufacturerProductId, null, null, null, null, null);
    }
    public CreateProductAuthorizationRequest(UUID manufacturerProductId, String requestNote) {
        this(manufacturerProductId, requestNote, null, null, null, null);
    }
}
