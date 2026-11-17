package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.OrganizationNotificationPreferences;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationNotificationPreferencesResponse(
        UUID organizationId,
        boolean dispatchExceptionsSms,
        boolean purchaseOrderApprovalsEmail,
        boolean weeklyCapacityForecasts,
        OffsetDateTime updatedAt
) {
    public static OrganizationNotificationPreferencesResponse from(
            OrganizationNotificationPreferences preferences) {
        return new OrganizationNotificationPreferencesResponse(
                preferences.getOrganizationId(), preferences.isDispatchExceptionsSms(),
                preferences.isPurchaseOrderApprovalsEmail(), preferences.isWeeklyCapacityForecasts(),
                preferences.getUpdatedAt());
    }
}
