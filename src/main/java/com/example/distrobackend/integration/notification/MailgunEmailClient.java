package com.example.distrobackend.integration.notification;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
public class MailgunEmailClient {

    private final RestTemplate restTemplate;

    @Value("${mailgun.api.key:}")
    private String apiKey;

    @Value("${mailgun.domain:}")
    private String domain;

    @Value("${mailgun.api.base-url:https://api.mailgun.net}")
    private String apiBaseUrl;

    public MailgunEmailClient(@Qualifier("darajaRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && domain != null && !domain.isBlank();
    }

    public void send(String recipient, String subject, String text) {
        if (!isConfigured()) {
            throw new IllegalStateException("Mailgun email provider is not configured");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Email recipient is required");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("api", apiKey);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("from", "Distro <postmaster@" + domain + ">");
        body.add("to", recipient);
        body.add("subject", subject);
        body.add("text", text);

        String baseUrl = apiBaseUrl.endsWith("/")
                ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1)
                : apiBaseUrl;
        restTemplate.postForEntity(baseUrl + "/v3/" + domain + "/messages",
                new HttpEntity<>(body, headers), String.class);
    }
}
