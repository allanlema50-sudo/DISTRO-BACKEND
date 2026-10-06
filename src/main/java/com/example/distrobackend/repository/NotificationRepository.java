package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Notification;
import com.example.distrobackend.Domain.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    List<Notification>
    findByRecipient_IdOrderByCreatedAtDesc(
            UUID recipientId
    );

    List<Notification>
    findByRecipient_IdAndStatusOrderByCreatedAtDesc(
            UUID recipientId,
            NotificationStatus status
    );

    long countByRecipient_IdAndStatus(
            UUID recipientId,
            NotificationStatus status
    );
}