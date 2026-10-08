package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.OrganizationNotification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrganizationNotificationResponse(
        UUID id,
        String notificationType,
        String title,
        String message,
        String referenceType,
        UUID referenceId,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
    public static OrganizationNotificationResponse from(OrganizationNotification notification) {
        return new OrganizationNotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
