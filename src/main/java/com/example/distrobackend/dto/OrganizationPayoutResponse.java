package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.OrganizationPayout;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationPayoutResponse(
        UUID id,
        UUID organizationId,
        BigDecimal amount,
        String currency,
        String bankReference,
        String idempotencyKey,
        OffsetDateTime createdAt
) {
    public static OrganizationPayoutResponse from(OrganizationPayout payout) {
        return new OrganizationPayoutResponse(
                payout.getId(),
                payout.getOrganization().getId(),
                payout.getAmount(),
                payout.getCurrency(),
                payout.getBankReference(),
                payout.getIdempotencyKey(),
                payout.getCreatedAt());
    }
}
