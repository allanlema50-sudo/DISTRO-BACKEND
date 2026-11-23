package com.example.distrobackend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProviderTransactionMatch(
        String providerTransactionId,
        String providerReference,
        String providerStatus,
        long providerAmountMinor,
        String providerCurrency,
        String paidAt,
        UUID paymentId,
        String localStatus,
        BigDecimal localAmount,
        String localCurrency,
        List<String> issues
) {
    public ProviderTransactionMatch {
        issues = List.copyOf(issues);
    }
}
