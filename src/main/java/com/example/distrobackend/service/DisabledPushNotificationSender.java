package com.example.distrobackend.service;

import com.example.distrobackend.event.PushNotificationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "firebase.push", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledPushNotificationSender implements PushNotificationSender {
    @Override
    public void send(PushNotificationEvent event) {
        // Push delivery is opt-in; in-app notifications remain available.
    }
}
