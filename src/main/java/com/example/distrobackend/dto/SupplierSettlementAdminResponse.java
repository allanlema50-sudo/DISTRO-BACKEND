package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.SupplierSettlement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SupplierSettlementAdminResponse(
        UUID id, UUID supplierOrganizationId, String supplierOrganizationName,
        UUID beneficiaryId, UUID settlementBatchId, BigDecimal amount, String currency,
        String status, String provider, String payoutReference, String providerMode, String executionMode,
        String providerTransferCode,
        int payoutAttempts, String failureReason, OffsetDateTime settledAt
) {
    public static SupplierSettlementAdminResponse from(SupplierSettlement settlement) {
        return new SupplierSettlementAdminResponse(
                settlement.getId(), settlement.getSupplierOrganization().getId(),
                settlement.getSupplierOrganization().getName(),
                settlement.getBeneficiary() == null ? null : settlement.getBeneficiary().getId(),
                settlement.getSettlementBatch() == null ? null : settlement.getSettlementBatch().getId(),
                settlement.getAmount(), settlement.getCurrency(), settlement.getStatus().name(),
                settlement.getProvider() == null ? null : settlement.getProvider().name(),
                settlement.getPayoutReference(),
                settlement.getSettlementBatch() == null ? null : settlement.getSettlementBatch().getProviderMode(),
                settlement.getSettlementBatch() == null ? null : settlement.getSettlementBatch().getExecutionMode(),
                settlement.getProviderTransferCode(),
                settlement.getPayoutAttempts(), settlement.getFailureReason(), settlement.getSettledAt());
    }
}
