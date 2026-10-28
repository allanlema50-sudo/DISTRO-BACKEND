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

import java.net.URI;

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
        headers.setBasicAuth("api", apiKey.trim());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        String mailgunDomain = domain.trim();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("from", "Distro <postmaster@" + mailgunDomain + ">");
        body.add("to", recipient.trim());
        body.add("subject", subject);
        body.add("text", text);

        String baseUrl = apiBaseUrl == null || apiBaseUrl.isBlank()
                ? "https://api.mailgun.net"
                : apiBaseUrl.trim();
        boolean quoted = baseUrl.length() >= 2
                && ((baseUrl.startsWith("\"") && baseUrl.endsWith("\""))
                || (baseUrl.startsWith("'") && baseUrl.endsWith("'")));
        if (quoted) {
            baseUrl = baseUrl.substring(1, baseUrl.length() - 1).trim();
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        URI apiOrigin;
        try {
            apiOrigin = URI.create(baseUrl);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "MAILGUN_API_BASE_URL must be https://api.mailgun.net or https://api.eu.mailgun.net", ex);
        }
        if (!"https".equalsIgnoreCase(apiOrigin.getScheme()) || apiOrigin.getHost() == null
                || (apiOrigin.getRawPath() != null && !apiOrigin.getRawPath().isEmpty())
                || apiOrigin.getRawQuery() != null || apiOrigin.getRawFragment() != null) {
            throw new IllegalStateException(
                    "MAILGUN_API_BASE_URL must contain only the HTTPS Mailgun API origin, e.g. https://api.mailgun.net");
        }

        restTemplate.postForEntity(apiOrigin + "/v3/" + mailgunDomain + "/messages",
                new HttpEntity<>(body, headers), String.class);
    }
}
