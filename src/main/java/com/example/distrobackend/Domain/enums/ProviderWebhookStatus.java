package com.example.distrobackend.Domain.enums;

public enum ProviderWebhookStatus {
    RECEIVED,
    PROCESSING,
    RETRY,
    PROCESSED,
    DEAD_LETTER
}
