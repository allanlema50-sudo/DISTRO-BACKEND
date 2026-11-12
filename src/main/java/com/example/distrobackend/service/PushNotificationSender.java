package com.example.distrobackend.service;

import com.example.distrobackend.event.PushNotificationEvent;

public interface PushNotificationSender {
    void send(PushNotificationEvent event);
}
