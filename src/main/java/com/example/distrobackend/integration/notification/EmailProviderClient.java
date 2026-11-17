package com.example.distrobackend.integration.notification;

/**
 * HTTPS (or local console) delivery used by OTP mail and onboarding notifications.
 * Implementations must not log credentials.
 */
public interface EmailProviderClient {

    boolean isConfigured();

    /**
     * Mailgun may fall back to the existing SMTP sender when it is not configured.
     * Brevo and log mode never do: Render's free tier blocks SMTP ports.
     */
    boolean allowsSmtpFallback();

    void send(String recipient, String subject, String text);
}
