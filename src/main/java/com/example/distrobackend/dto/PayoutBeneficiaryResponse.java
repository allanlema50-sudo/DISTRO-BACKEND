package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.PayoutBeneficiary;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PayoutBeneficiaryResponse(
        UUID id, UUID organizationId, String method, String provider, String currency,
        String accountName, String accountMask, String bankCode, String status,
        String providerRecipientCode, String providerMode, OffsetDateTime createdAt, OffsetDateTime reviewedAt,
        String reviewNote
) {
    public static PayoutBeneficiaryResponse from(PayoutBeneficiary beneficiary) {
        return new PayoutBeneficiaryResponse(
                beneficiary.getId(), beneficiary.getOrganization().getId(), beneficiary.getMethod().name(),
                beneficiary.getProvider().name(), beneficiary.getCurrency(), beneficiary.getAccountName(),
                beneficiary.getAccountMask(), beneficiary.getBankCode(), beneficiary.getStatus().name(),
                beneficiary.getProviderRecipientCode(), beneficiary.getProviderMode(),
                beneficiary.getCreatedAt(), beneficiary.getReviewedAt(),
                beneficiary.getReviewNote());
    }
}
