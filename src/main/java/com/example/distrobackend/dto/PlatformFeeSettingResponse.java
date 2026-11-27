package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.PlatformFeeSetting;
import com.example.distrobackend.Domain.enums.FeePayer;
import com.example.distrobackend.Domain.enums.PlatformFeeType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PlatformFeeSettingResponse(
        String scope,
        PlatformFeeType feeType,
        BigDecimal feeValue,
        String currency,
        FeePayer feePayer,
        long version,
        OffsetDateTime updatedAt
) {
    public static PlatformFeeSettingResponse from(PlatformFeeSetting setting) {
        return new PlatformFeeSettingResponse(setting.getScope(), setting.getFeeType(), setting.getFeeValue(),
                setting.getCurrency(), setting.getFeePayer(), setting.getVersion(), setting.getUpdatedAt());
    }
}
