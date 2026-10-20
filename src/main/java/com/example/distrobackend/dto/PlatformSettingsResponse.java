package com.example.distrobackend.dto;

import java.math.BigDecimal;

public record PlatformSettingsResponse(
        boolean maintenanceMode,
        String supportEmail,
        BigDecimal defaultDeliveryFee
) {}
