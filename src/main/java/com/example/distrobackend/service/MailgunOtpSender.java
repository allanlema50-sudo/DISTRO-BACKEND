package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import com.example.distrobackend.integration.notification.MailgunEmailClient;

@Slf4j
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
            log.info("Mailgun OTP dispatched for purpose={} to domain={}",
                    purpose, recipientDomain(recipient));
        } catch (HttpClientErrorException ex) {
            // 4xx: bad API key, domain not found, recipient not authorized on sandbox, etc.
            log.error("Mailgun rejected OTP request: status={} body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Mailgun OTP provider unavailable");
        } catch (HttpServerErrorException ex) {
            // 5xx: Mailgun-side failure
            log.error("Mailgun server error sending OTP: status={} body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Mailgun OTP provider unavailable");
        } catch (RuntimeException ex) {
            log.error("Mailgun OTP delivery failed: {}", ex.getMessage(), ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Mailgun OTP provider unavailable");
        }
    }

    private static String recipientDomain(String email) {
        int at = email.indexOf('@');
        return at >= 0 ? email.substring(at) : "<no-domain>";
    }
}
