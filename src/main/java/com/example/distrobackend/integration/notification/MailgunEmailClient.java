package com.example.distrobackend.integration.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
public class MailgunEmailClient implements EmailProviderClient {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String domain;
    private final String apiBaseUrl;

    public MailgunEmailClient(RestTemplate restTemplate, String apiKey, String domain, String apiBaseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = EmailValues.normalize(apiKey);
        this.domain = EmailValues.normalize(domain);
        this.apiBaseUrl = EmailValues.normalize(apiBaseUrl);
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !domain.isBlank();
    }

    @Override
    public boolean allowsSmtpFallback() {
        return true;
    }

    @Override
    public void send(String recipient, String subject, String text) {
        if (!isConfigured()) {
            throw new IllegalStateException("Mailgun email provider is not configured");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Email recipient is required");
        }

        String normalizedDomain = domain;
        URI endpoint = messageEndpoint(normalizedDomain);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("api", apiKey);
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
        String baseUrl = apiBaseUrl;
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
}
