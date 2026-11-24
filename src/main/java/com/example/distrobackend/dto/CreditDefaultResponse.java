package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.PlatformCreditDefault;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreditDefaultResponse(
        String currency,
        BigDecimal defaultLimit,
        int defaultTermDays,
        long version,
        OffsetDateTime updatedAt
) {
    public static CreditDefaultResponse from(PlatformCreditDefault value) {
        return new CreditDefaultResponse(value.getCurrency(), value.getDefaultLimit(), value.getDefaultTermDays(),
                value.getVersion(), value.getUpdatedAt());
    }
}
