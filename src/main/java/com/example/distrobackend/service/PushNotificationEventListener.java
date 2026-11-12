package com.example.distrobackend.service;

import com.example.distrobackend.event.PushNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationEventListener {

    private final PushNotificationSender pushNotificationSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(PushNotificationEvent event) {
        try {
            pushNotificationSender.send(event);
        } catch (RuntimeException ex) {
            // Push delivery is best effort; a provider outage must not undo an in-app notification.
            log.warn("Push notification delivery failed for notification {}: {}",
                    event.notificationId(), ex.getMessage());
        }
    }
}
