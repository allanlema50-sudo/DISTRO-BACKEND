package com.example.distrobackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdatePlatformSettingsRequest(
        Boolean maintenanceMode,
        @Email @Size(max = 150) String supportEmail,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal defaultDeliveryFee
) {}
