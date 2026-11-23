package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.CommissionRule;
import com.example.distrobackend.Domain.enums.Organizationtype;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CommissionRuleResponse(
        UUID id,
        BigDecimal ratePercent,
        BigDecimal fixedFee,
        String currency,
        Organizationtype organizationType,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveUntil,
        boolean active,
        UUID createdBy,
        OffsetDateTime createdAt
) {
    public static CommissionRuleResponse from(CommissionRule rule) {
        return new CommissionRuleResponse(rule.getId(), rule.getRatePercent(), rule.getFixedFee(),
                rule.getCurrency(), rule.getOrganizationType(), rule.getEffectiveFrom(),
                rule.getEffectiveUntil(), rule.isActive(),
                rule.getCreatedBy() == null ? null : rule.getCreatedBy().getId(), rule.getCreatedAt());
    }
}
