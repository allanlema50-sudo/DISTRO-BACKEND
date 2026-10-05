package com.example.distrobackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingAccessEmailSender implements AccessEmailSender {
    @Override public void sendActivation(String email, String activationUrl) {
        log.info("[DEV ACTIVATION EMAIL] to={} link={}", email, activationUrl);
    }
    @Override public void notifySuperAdmins(String subject, String body) {
        log.info("[DEV SUPER ADMIN NOTIFICATION] subject={} body={}", subject, body);
    }
}
