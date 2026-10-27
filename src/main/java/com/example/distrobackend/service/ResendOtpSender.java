package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile("prod")
@ConditionalOnProperty(prefix = "otp", name = "provider", havingValue = "resend")
public class ResendOtpSender implements OtpSender {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${resend.api.key}")
    private String resendApiKey;

    @Value("${resend.from.email:onboarding@resend.dev}")
    private String fromEmail;

    @Override
    public void send(User user, OtpPurpose purpose, String code, long ttlMinutes) {
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            sendViaEmail(user.getEmail(), purpose, code, ttlMinutes);
        }
        else {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "OTP delivery is not configured for users without an email address");
        }
    }

    private void sendViaEmail(String email, OtpPurpose purpose, String code, long ttlMinutes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("from", "Distro <" + fromEmail + ">");
            body.put("to", List.of(email));
            body.put("subject", "Your Distro Verification Code");
            body.put("html", "<h2>Distro Verification</h2><p>Your " + purpose + " code is: <strong>" + code + "</strong></p><p>This code expires in " + ttlMinutes + " minutes.</p>");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject("https://api.resend.com/emails", request, String.class);
            log.info("Successfully sent OTP email via Resend to {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}", email, e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "OTP provider unavailable");
        }
    }
}
