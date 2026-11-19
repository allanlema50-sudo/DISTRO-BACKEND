package com.example.distrobackend.integration.notification;

/**
 * Provider rejected or failed an email send. The message is status-only so logs
 * that print the exception cannot include an API key.
 */
public class EmailDeliveryException extends RuntimeException {

    private final int statusCode;

    public EmailDeliveryException(int statusCode) {
        super("Email provider returned HTTP " + statusCode);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
