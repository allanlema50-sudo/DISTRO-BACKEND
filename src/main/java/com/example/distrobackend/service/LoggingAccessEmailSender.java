package com.example.distrobackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!prod")
public class LoggingAccessEmailSender implements AccessEmailSender {
    @Override public void sendActivation(String email, String activationUrl) {
        log.info("[DEV ACTIVATION EMAIL] to={} link={}", email, activationUrl);
    }
    @Override public void notifySuperAdmins(String subject, String body) {
        log.info("[DEV SUPER ADMIN NOTIFICATION] subject={} body={}", subject, body);
    }
}
