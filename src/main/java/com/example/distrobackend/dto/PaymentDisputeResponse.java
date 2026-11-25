package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.PaymentDispute;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentDisputeResponse(
        UUID id,
        String providerDisputeId,
        UUID paymentId,
        String transactionReference,
        String status,
        String resolution,
        BigDecimal disputedAmount,
        String currency,
        OffsetDateTime dueAt,
        String lastEventName,
        OffsetDateTime lastEventAt,
        int reminderCount,
        String reviewNote,
        java.util.UUID clearedBy,
        OffsetDateTime clearedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PaymentDisputeResponse from(PaymentDispute dispute) {
        return new PaymentDisputeResponse(dispute.getId(), dispute.getProviderDisputeId(),
                dispute.getPayment() == null ? null : dispute.getPayment().getId(),
                dispute.getTransactionReference(), dispute.getStatus().name(), dispute.getResolution(),
                dispute.getDisputedAmount(), dispute.getCurrency(), dispute.getDueAt(),
                dispute.getLastEventName(), dispute.getLastEventAt(), dispute.getReminderCount(),
                dispute.getReviewNote(), dispute.getClearedBy() == null ? null : dispute.getClearedBy().getId(),
                dispute.getClearedAt(), dispute.getCreatedAt(), dispute.getUpdatedAt());
    }
}
