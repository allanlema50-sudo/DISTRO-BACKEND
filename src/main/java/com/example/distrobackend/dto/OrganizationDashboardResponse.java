package com.example.distrobackend.dto;

import java.util.UUID;

public record OrganizationDashboardResponse(
        UUID organizationId,
        long members,
        long stockItems,
        long orders,
        long payments,
        long trips
) {}
