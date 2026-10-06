package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Notification;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationStatus;
import com.example.distrobackend.Domain.enums.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(

        UUID id,

        String title,

        String message,

        NotificationType type,

        NotificationPriority priority,

        NotificationStatus status,

        UUID referenceId,

        String referenceType,

        OffsetDateTime createdAt,

        OffsetDateTime readAt

) {

    public static NotificationResponse from(
            Notification notification
    ) {

        return new NotificationResponse(

                notification.getId(),

                notification.getTitle(),

                notification.getMessage(),

                notification.getType(),

                notification.getPriority(),

                notification.getStatus(),

                notification.getReferenceId(),

                notification.getReferenceType(),

                notification.getCreatedAt(),

                notification.getReadAt()

        );
    }
}