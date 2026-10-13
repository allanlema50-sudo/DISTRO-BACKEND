package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Notification;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationStatus;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
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
    public Notification create(User recipient, String title, String message,
                               NotificationType type, NotificationPriority priority,
                               UUID referenceId, String referenceType) {
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
    public List<com.example.distrobackend.dto.NotificationResponse> list(UUID userId) {
        return notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(userId).stream()
                .map(com.example.distrobackend.dto.NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByRecipient_IdAndStatus(userId, NotificationStatus.UNREAD);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Notification not found"));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(OffsetDateTime.now());
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        List<Notification> notifications = notificationRepository
                .findByRecipient_IdAndStatusOrderByCreatedAtDesc(userId, NotificationStatus.UNREAD);
        OffsetDateTime now = OffsetDateTime.now();
        notifications.forEach(notification -> {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(now);
        });
    }
}
