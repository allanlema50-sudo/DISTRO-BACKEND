package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Notification;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationStatus;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification create(
            User recipient,
            String title,
            String message,
            NotificationType type,
            NotificationPriority priority,
            UUID referenceId,
            String referenceType
    ) {

        Notification notification = new Notification();

        notification.setRecipient(recipient);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setPriority(priority);
        notification.setStatus(NotificationStatus.UNREAD);
        notification.setReferenceId(referenceId);
        notification.setReferenceType(referenceType);

        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getForUser(UUID userId) {

        return notificationRepository
                .findByRecipient_IdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void markAsRead(
            UUID notificationId,
            UUID userId
    ) {

        Notification notification =
                notificationRepository.findById(notificationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Notification not found"
                                )
                        );

        if (!notification
                .getRecipient()
                .getId()
                .equals(userId)) {

            throw new IllegalArgumentException(
                    "Notification does not belong to this user"
            );
        }

        notification.setStatus(
                NotificationStatus.READ
        );

        notification.setReadAt(
                OffsetDateTime.now()
        );

        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {

        List<Notification> notifications =
                notificationRepository
                        .findByRecipient_IdAndStatusOrderByCreatedAtDesc(
                                userId,
                                NotificationStatus.UNREAD
                        );

        OffsetDateTime now =
                OffsetDateTime.now();

        for (Notification notification : notifications) {

            notification.setStatus(
                    NotificationStatus.READ
            );

            notification.setReadAt(now);
        }

        notificationRepository.saveAll(
                notifications
        );
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {

        return notificationRepository
                .countByRecipient_IdAndStatus(
                        userId,
                        NotificationStatus.UNREAD
                );
    }
}