package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.example.distrobackend.integration.notification.MailgunEmailClient;

@Component
@Profile("prod")
@ConditionalOnProperty(prefix = "otp", name = "provider", havingValue = "mailgun")
public class MailgunOtpSender implements OtpSender {

    private final MailgunEmailClient mailgunEmailClient;

    public MailgunOtpSender(MailgunEmailClient mailgunEmailClient) {
        this.mailgunEmailClient = mailgunEmailClient;
    }

    @Override
    public void send(User user, OtpPurpose purpose, String code, long ttlMinutes) {
        String recipient = user.getEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Email OTP delivery requires an email address on the account");
        }
        if (!mailgunEmailClient.isConfigured()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Mailgun OTP provider is not configured");
        }

        try {
            mailgunEmailClient.send(recipient, "Your Distro verification code",
                    "Your " + purpose.name().toLowerCase().replace('_', ' ')
                            + " code is " + code + ". It expires in " + ttlMinutes + " minutes.");
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Mailgun OTP provider unavailable");
        }
    }
}
