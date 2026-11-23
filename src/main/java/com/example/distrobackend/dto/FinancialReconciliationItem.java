package com.example.distrobackend.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FinancialReconciliationItem(
        UUID paymentId,
        String checkoutReference,
        String providerReference,
        String paymentStatus,
        BigDecimal paymentAmount,
        String currency,
        OffsetDateTime initiatedAt,
        BigDecimal allocatedGross,
        int allocationCount,
        int captureJournalCount,
        int refundJournalCount,
        int unbalancedCaptureJournalCount,
        int settlementCount,
        BigDecimal settledAmount,
        int payoutJournalCount,
        int reversedPayoutJournalCount,
        int unbalancedPayoutJournalCount,
        String latestRefundStatus,
        int disputeCount,
        List<String> issues
) {
    public FinancialReconciliationItem {
        issues = List.copyOf(issues);
    }
}
