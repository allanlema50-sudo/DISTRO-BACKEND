package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.PushDeviceToken;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PushDeviceTokenResponse(UUID id, String platform, OffsetDateTime updatedAt) {
    public static PushDeviceTokenResponse from(PushDeviceToken token) {
        return new PushDeviceTokenResponse(token.getId(), token.getPlatform(), token.getUpdatedAt());
    }
}
