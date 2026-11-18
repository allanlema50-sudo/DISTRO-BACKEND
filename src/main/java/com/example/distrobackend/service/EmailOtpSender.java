package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.integration.notification.EmailDeliveryException;
import com.example.distrobackend.integration.notification.EmailProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * Sends account and delivery OTPs through the selected {@link EmailProviderClient}.
 * Active for OTP_PROVIDER=mailgun and OTP_PROVIDER=brevo on every profile, so a
 * local Brevo trial does not require the prod profile.
 */
@Slf4j
@Component
@ConditionalOnExpression("'mailgun'.equalsIgnoreCase('${otp.provider:http}') or 'brevo'.equalsIgnoreCase('${otp.provider:http}')")
public class EmailOtpSender implements OtpSender {

    private final EmailProviderClient emailProviderClient;

    public EmailOtpSender(EmailProviderClient emailProviderClient) {
        this.emailProviderClient = emailProviderClient;
    }

    @Override
    public void send(User user, OtpPurpose purpose, String code, long ttlMinutes) {
        String recipient = user.getEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Email OTP delivery requires an email address on the account");
        }
        if (!emailProviderClient.isConfigured()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Email OTP provider is not configured");
        }

        try {
            emailProviderClient.send(recipient, "Your Distro verification code",
                    "Your " + purpose.name().toLowerCase().replace('_', ' ')
                            + " code is " + code + ". It expires in " + ttlMinutes + " minutes.");
            log.info("Email OTP dispatched for purpose={} to domain={}",
                    purpose, recipientDomain(recipient));
        } catch (EmailDeliveryException ex) {
            // Status and body were logged by the provider client, without the API key.
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Email OTP provider unavailable");
        } catch (HttpStatusCodeException ex) {
            log.error("Email provider rejected OTP request: status={} body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Email OTP provider unavailable");
        } catch (RuntimeException ex) {
            log.error("Email OTP delivery failed: {}", ex.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Email OTP provider unavailable");
        }
    }

    private static String recipientDomain(String email) {
        int at = email.indexOf('@');
        return at >= 0 ? email.substring(at) : "<no-domain>";
    }
}
