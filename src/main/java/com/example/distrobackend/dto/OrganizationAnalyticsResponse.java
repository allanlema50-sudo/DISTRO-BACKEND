package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.Organizationtype;

import java.util.UUID;

public record OrganizationAnalyticsResponse(
        UUID organizationId,
        String name,
        Organizationtype type,
        long users,
        long orders,
        long payments,
        long stockItems,
        long trips
) {}
