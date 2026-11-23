package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.LedgerEntry;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        UUID accountId,
        String accountType,
        UUID organizationId,
        String direction,
        BigDecimal amount,
        String currency,
        String description
) {
    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(), entry.getAccount().getId(),
                entry.getAccount().getAccountType().name(),
                entry.getAccount().getOrganization() == null ? null : entry.getAccount().getOrganization().getId(),
                entry.getDirection().name(), entry.getAmount(), entry.getCurrency(), entry.getDescription());
    }
}
