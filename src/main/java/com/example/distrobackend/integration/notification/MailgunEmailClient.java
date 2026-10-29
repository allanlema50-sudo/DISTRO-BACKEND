package com.example.distrobackend.integration.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
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
        return !normalize(apiKey).isBlank() && !normalize(domain).isBlank();
    }

    public void send(String recipient, String subject, String text) {
        if (!isConfigured()) {
            throw new IllegalStateException("Mailgun email provider is not configured");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Email recipient is required");
        }

        String normalizedDomain = normalize(domain);
        URI endpoint = messageEndpoint(normalizedDomain);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("api", normalize(apiKey));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("from", "Distro <postmaster@" + normalizedDomain + ">");
        body.add("to", recipient);
        body.add("subject", subject);
        body.add("text", text);

        log.debug("Mailgun POST {} to={} from=postmaster@{}", endpoint, recipient, normalizedDomain);

        ResponseEntity<String> response = restTemplate.postForEntity(
                endpoint, new HttpEntity<>(body, headers), String.class);

        log.debug("Mailgun response: status={} body={}", response.getStatusCode(), response.getBody());
    }

    private URI messageEndpoint(String normalizedDomain) {
        String baseUrl = normalize(apiBaseUrl);
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("MAILGUN_API_BASE_URL must be configured");
        }
        if (!normalizedDomain.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")) {
            throw new IllegalStateException("MAILGUN_DOMAIN must be a valid Mailgun domain");
        }
        try {
            URI baseUri = URI.create(baseUrl);
            if (!("https".equalsIgnoreCase(baseUri.getScheme())
                    || "http".equalsIgnoreCase(baseUri.getScheme()))
                    || baseUri.getHost() == null) {
                throw new IllegalStateException("MAILGUN_API_BASE_URL must be an absolute HTTP(S) URL");
            }
            return UriComponentsBuilder.fromUri(baseUri)
                    .pathSegment("v3", normalizedDomain, "messages")
                    .build()
                    .toUri();
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("MAILGUN_API_BASE_URL must be an absolute HTTP(S) URL", ex);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() >= 2
                && ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'")))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }
}
