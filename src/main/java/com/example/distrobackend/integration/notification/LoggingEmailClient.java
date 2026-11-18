package com.example.distrobackend.integration.notification;

import lombok.extern.slf4j.Slf4j;

/**
 * Prints the outbound email, including any OTP in the body, to the application log.
 * The provider configuration refuses to select this client unless the local profile is active.
 */
@Slf4j
public class LoggingEmailClient implements EmailProviderClient {

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public boolean allowsSmtpFallback() {
        return false;
    }

    @Override
    public void send(String recipient, String subject, String text) {
        log.info("[DEV EMAIL] to={} subject={} body={}", recipient, subject, text);
    }
}
