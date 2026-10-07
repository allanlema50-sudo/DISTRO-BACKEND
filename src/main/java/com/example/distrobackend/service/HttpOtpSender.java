package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Production SMS adapter. The configured provider must accept the documented
 * JSON contract; provider-specific behavior belongs behind this adapter.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class HttpOtpSender implements OtpSender {

    private final RestTemplate darajaRestTemplate;

    @Value("${otp.sms.endpoint}")
    private String endpoint;
    @Value("${otp.sms.api-key}")
    private String apiKey;
    @Value("${otp.sms.sender-id}")
    private String senderId;

    @Override
    public void send(String phoneNumber, OtpPurpose purpose, String code, long ttlMinutes) {
        if (endpoint == null || endpoint.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Production OTP provider is not configured");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);
        Map<String, String> body = Map.of(
                "to", phoneNumber,
                "senderId", senderId,
                "message", "Your LogiFlow verification code is " + code
                        + ". It expires in " + ttlMinutes + " minutes.");
        try {
            darajaRestTemplate.postForEntity(endpoint, new HttpEntity<>(body, headers), Void.class);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "OTP provider unavailable");
        }
    }
}
