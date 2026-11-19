package com.example.distrobackend.service;

import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.integration.notification.EmailProviderClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Sends the temporary driver password through the same email provider used for
 * verification codes. Onboarding mail is a separate switch and is not required.
 */
@Service
@RequiredArgsConstructor
public class DriverLoginEmailService {

    private final EmailProviderClient emailProviderClient;

    public void sendTemporaryPassword(String recipient, String temporaryPassword) {
        if (recipient == null || recipient.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "This driver has no email address");
        }
        if (!emailProviderClient.isConfigured()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Email delivery is not configured, so the sign-in details could not be sent");
        }
        String body = """
                Your driver account is approved.

                Open the MyDistro app, choose Driver, and sign in with this email and temporary password.
                After you sign in, change the password from your profile.

                Email: %s
                Temporary password: %s
                """.formatted(recipient.trim(), temporaryPassword);
        try {
            emailProviderClient.send(recipient.trim(), "Your MyDistro driver sign-in", body);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "The sign-in email could not be sent. Use resend from the driver profile.");
        }
    }
}
