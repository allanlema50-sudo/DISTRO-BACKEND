package com.example.distrobackend.integration.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Sends mail through Brevo's HTTPS API. SMTP is intentionally not used.
 */
@Slf4j
public class BrevoEmailClient implements EmailProviderClient {

    static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;

    public BrevoEmailClient(RestTemplate restTemplate, String apiKey, String senderEmail, String senderName) {
        this.restTemplate = restTemplate;
        this.apiKey = EmailValues.normalize(apiKey);
        this.senderEmail = EmailValues.normalize(senderEmail);
        this.senderName = EmailValues.normalize(senderName);
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !senderEmail.isBlank() && !senderName.isBlank();
    }

    @Override
    public boolean allowsSmtpFallback() {
        return false;
    }

    @Override
    public void send(String recipient, String subject, String text) {
        if (!isConfigured()) {
            throw new IllegalStateException("Brevo email provider is not configured");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Email recipient is required");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);

        String payload = "{"
                + "\"sender\":{\"name\":" + json(senderName) + ",\"email\":" + json(senderEmail) + "},"
                + "\"to\":[{\"email\":" + json(recipient.trim()) + "}],"
                + "\"subject\":" + json(subject == null ? "" : subject) + ","
                + "\"htmlContent\":" + json(toHtml(text))
                + "}";

        log.debug("Brevo POST {} toDomain={}", ENDPOINT, domainOf(recipient));

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    ENDPOINT, new HttpEntity<>(payload, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logFailure(response.getStatusCode().value(), response.getBody());
                throw new EmailDeliveryException(response.getStatusCode().value());
            }
            log.info("Brevo accepted email to domain={}", domainOf(recipient));
        } catch (RestClientResponseException ex) {
            logFailure(ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new EmailDeliveryException(ex.getStatusCode().value());
        }
    }

    private void logFailure(int status, String body) {
        log.error("Brevo email failed: status={} body={}", status, redact(body));
    }

    private String redact(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String sanitized = body;
        if (!apiKey.isBlank()) {
            sanitized = sanitized.replace(apiKey, "[redacted]");
        }
        if (sanitized.length() > 4000) {
            return sanitized.substring(0, 4000) + "...[truncated]";
        }
        return sanitized;
    }

    private static String toHtml(String text) {
        String value = text == null ? "" : text;
        return "<p>" + value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>")
                + "</p>";
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }

    private static String domainOf(String email) {
        int at = email.indexOf('@');
        return at >= 0 ? email.substring(at) : "<no-domain>";
    }
}
