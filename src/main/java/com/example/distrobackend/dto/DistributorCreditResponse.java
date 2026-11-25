package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.CreditTermStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Credit a manufacturer extends to one distributor, with what the platform would apply today. */
public record DistributorCreditResponse(
        UUID distributorOrganizationId,
        String distributorName,
        String currency,
        boolean hasRelationshipTerms,
        BigDecimal creditLimit,
        Integer termDays,
        CreditTermStatus status,
        Long version,
        /** RELATIONSHIP, PLATFORM_DEFAULT or NONE. */
        String effectiveSource,
        BigDecimal effectiveLimit,
        Integer effectiveTermDays,
        BigDecimal outstanding,
        BigDecimal available,
        long overdueOrders
) {}
