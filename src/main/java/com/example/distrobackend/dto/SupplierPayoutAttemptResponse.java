package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.SupplierPayoutAttempt;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierPayoutAttemptResponse(
        UUID id,
        int attemptNumber,
        String providerReference,
        String providerMode,
        String providerTransferCode,
        String status,
        BigDecimal amount,
        String currency,
        String failureReason,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
    public static SupplierPayoutAttemptResponse from(SupplierPayoutAttempt attempt) {
        return new SupplierPayoutAttemptResponse(attempt.getId(), attempt.getAttemptNumber(),
                attempt.getProviderReference(), attempt.getProviderMode(), attempt.getProviderTransferCode(),
                attempt.getStatus().name(),
                attempt.getAmount(), attempt.getCurrency(), attempt.getFailureReason(),
                attempt.getStartedAt(), attempt.getCompletedAt());
    }
}
