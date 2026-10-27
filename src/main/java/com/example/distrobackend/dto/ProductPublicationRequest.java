package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;

public record ProductPublicationRequest(@NotNull PublicationStatus status) {
    public enum PublicationStatus {
        DRAFT,
        PUBLISHED,
        DISCONTINUED
    }
}
