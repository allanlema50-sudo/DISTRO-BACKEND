package com.example.distrobackend.dto;

public record OrganizationNotificationPreferencesRequest(
        Boolean dispatchExceptionsSms,
        Boolean purchaseOrderApprovalsEmail,
        Boolean weeklyCapacityForecasts
) {}
