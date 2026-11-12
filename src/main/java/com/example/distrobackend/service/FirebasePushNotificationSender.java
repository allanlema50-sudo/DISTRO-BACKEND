package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PushDeviceToken;
import com.example.distrobackend.event.PushNotificationEvent;
import com.example.distrobackend.repository.PushDeviceTokenRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "firebase.push", name = "enabled", havingValue = "true")
public class FirebasePushNotificationSender implements PushNotificationSender {

    private static final int MAX_MULTICAST_TOKENS = 500;

    private final FirebaseMessaging firebaseMessaging;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;

    @Override
    public void send(PushNotificationEvent event) {
        List<PushDeviceToken> registrations = pushDeviceTokenRepository.findAllByUser_Id(event.recipientId());
        if (registrations.isEmpty()) return;

        for (int start = 0; start < registrations.size(); start += MAX_MULTICAST_TOKENS) {
            List<PushDeviceToken> batch = registrations.subList(start,
                    Math.min(start + MAX_MULTICAST_TOKENS, registrations.size()));
            sendBatch(event, batch);
        }
    }

    private void sendBatch(PushNotificationEvent event, List<PushDeviceToken> registrations) {
        List<String> tokens = registrations.stream().map(PushDeviceToken::getDeviceToken).toList();
        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder().setTitle(event.title()).setBody(event.message()).build())
                .putAllData(data(event))
                .addAllTokens(tokens)
                .build();
        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
            List<String> invalidTokens = new ArrayList<>();
            List<SendResponse> sendResponses = response.getResponses();
            for (int index = 0; index < sendResponses.size(); index++) {
                SendResponse sendResponse = sendResponses.get(index);
                if (!sendResponse.isSuccessful() && sendResponse.getException() != null
                        && sendResponse.getException().getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                    invalidTokens.add(tokens.get(index));
                }
            }
            if (!invalidTokens.isEmpty()) pushDeviceTokenRepository.deleteByDeviceTokenIn(invalidTokens);
            if (response.getFailureCount() > 0) {
                log.warn("Firebase push delivery had {} failures for notification {}",
                        response.getFailureCount(), event.notificationId());
            }
        } catch (FirebaseMessagingException | RuntimeException ex) {
            log.warn("Firebase push delivery failed for notification {}: {}",
                    event.notificationId(), ex.getMessage());
        }
    }

    private static Map<String, String> data(PushNotificationEvent event) {
        Map<String, String> payload = new java.util.HashMap<>();
        payload.put("notificationId", event.notificationId().toString());
        if (event.type() != null) payload.put("type", event.type().name());
        if (event.priority() != null) payload.put("priority", event.priority().name());
        if (event.referenceId() != null) payload.put("referenceId", event.referenceId().toString());
        if (event.referenceType() != null) payload.put("referenceType", event.referenceType());
        return payload;
    }
}
