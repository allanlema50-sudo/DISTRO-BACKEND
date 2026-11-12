package com.example.distrobackend.event;

import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;

import java.util.UUID;

public record PushNotificationEvent(UUID recipientId, UUID notificationId, String title, String message,
                                    NotificationType type, NotificationPriority priority,
                                    UUID referenceId, String referenceType) { }
