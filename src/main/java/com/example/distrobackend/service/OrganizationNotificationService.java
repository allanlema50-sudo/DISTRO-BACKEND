package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.OrganizationNotification;
import com.example.distrobackend.dto.OrganizationNotificationResponse;
import com.example.distrobackend.repository.OrganizationNotificationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationNotificationService {

    private final OrganizationNotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Uses a new transaction so a stock-rejection notification survives the
     * transaction that rejects the order.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrganizationNotification notify(
            Organization organization,
            String type,
            String title,
            String message,
            String referenceType,
            UUID referenceId) {
        OrganizationNotification notification = new OrganizationNotification();
        notification.setOrganization(organization);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        OrganizationNotification saved = notificationRepository.saveAndFlush(notification);
        try {
            messagingTemplate.convertAndSend(
                    "/topic/organizations/" + organization.getId() + "/notifications",
                    OrganizationNotificationResponse.from(saved));
        } catch (RuntimeException ex) {
            // The database notification is the source of truth; a broker outage
            // must not turn a correctly rejected order into an internal error.
            log.warn("Could not publish organization notification {} over WebSocket", saved.getId(), ex);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<OrganizationNotificationResponse> list(AuthenticatedUser user, Pageable pageable) {
        if (user == null || user.organizationId() == null) {
            throw new com.example.distrobackend.Exception.ApiException(
                    com.example.distrobackend.Exception.ErrorCode.ACCESS_DENIED);
        }
        return notificationRepository
                .findByOrganization_IdOrderByCreatedAtDesc(user.organizationId(), pageable)
                .map(OrganizationNotificationResponse::from);
    }
}
