package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
@Profile("prod")
@ConditionalOnProperty(prefix = "otp", name = "provider", havingValue = "mailgun")
public class MailgunOtpSender implements OtpSender {

    private final RestTemplate restTemplate;

    public MailgunOtpSender(@Qualifier("darajaRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${mailgun.api.key:}")
    private String apiKey;

    @Value("${mailgun.domain:}")
    private String domain;

    @Value("${mailgun.api.base-url:https://api.mailgun.net}")
    private String apiBaseUrl;

    @Override
    public void send(User user, OtpPurpose purpose, String code, long ttlMinutes) {
        String recipient = user.getEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Email OTP delivery requires an email address on the account");
        }
        if (apiKey == null || apiKey.isBlank() || domain == null || domain.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Mailgun OTP provider is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("api", apiKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("from", "Distro <postmaster@" + domain + ">");
        body.add("to", recipient);
        body.add("subject", "Your Distro verification code");
        body.add("text", "Your " + purpose.name().toLowerCase().replace('_', ' ')
                + " code is " + code + ". It expires in " + ttlMinutes + " minutes.");

        try {
            restTemplate.postForEntity(apiBaseUrl + "/v3/" + domain + "/messages",
                    new HttpEntity<>(body, headers), String.class);
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Mailgun OTP provider unavailable");
        }
    }
}
