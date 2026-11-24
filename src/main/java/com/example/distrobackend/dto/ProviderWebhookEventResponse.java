package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.ProviderWebhookEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProviderWebhookEventResponse(
        UUID id, String provider, String eventName, String providerReference, String status,
        int attemptCount, OffsetDateTime nextAttemptAt, String lastError,
        OffsetDateTime processedAt, OffsetDateTime receivedAt
) {
    public static ProviderWebhookEventResponse from(ProviderWebhookEvent event) {
        return new ProviderWebhookEventResponse(event.getId(), event.getProvider(), event.getEventName(),
                event.getProviderReference(), event.getStatus().name(), event.getAttemptCount(),
                event.getNextAttemptAt(), event.getLastError(), event.getProcessedAt(), event.getReceivedAt());
    }
}
