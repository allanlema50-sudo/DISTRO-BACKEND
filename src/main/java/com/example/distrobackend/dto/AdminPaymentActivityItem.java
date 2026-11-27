package com.example.distrobackend.dto;

import com.example.distrobackend.repository.AdminPaymentActivityRepository.ActivityRow;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPaymentActivityItem(
        UUID transactionId,
        String transactionType,
        String provider,
        String providerMode,
        String status,
        String method,
        BigDecimal amount,
        String currency,
        String providerReference,
        OffsetDateTime initiatedAt,
        OffsetDateTime confirmedAt,
        UUID organizationId,
        String organizationName,
        String initiatorEmail,
        String contextReference,
        boolean demo,
        BigDecimal allocatedGross,
        BigDecimal providerFees,
        BigDecimal commission,
        BigDecimal supplierPayable,
        long settlementCount,
        String settlementStatuses,
        long refundCount,
        BigDecimal refundedAmount,
        String refundStatuses,
        long disputeCount,
        long openDisputeCount) {

    public static AdminPaymentActivityItem from(ActivityRow row) {
        return new AdminPaymentActivityItem(row.getTransactionId(), row.getTransactionType(), row.getProvider(),
                row.getProviderMode(), row.getStatus(), row.getMethod(), row.getAmount(), row.getCurrency(),
                row.getProviderReference(), row.getInitiatedAt(), row.getConfirmedAt(), row.getOrganizationId(),
                row.getOrganizationName(), row.getInitiatorEmail(), row.getContextReference(), row.getDemo(), row.getAllocatedGross(),
                row.getProviderFees(), row.getCommission(), row.getSupplierPayable(), row.getSettlementCount(),
                row.getSettlementStatuses(), row.getRefundCount(), row.getRefundedAmount(), row.getRefundStatuses(),
                row.getDisputeCount(), row.getOpenDisputeCount());
    }
}
