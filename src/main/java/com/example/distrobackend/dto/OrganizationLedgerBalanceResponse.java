package com.example.distrobackend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrganizationLedgerBalanceResponse(
        UUID organizationId,
        String currency,
        BigDecimal payableBalance
) {}
