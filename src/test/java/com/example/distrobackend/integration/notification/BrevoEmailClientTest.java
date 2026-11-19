package com.example.distrobackend.integration.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class BrevoEmailClientTest {

    private static final String API_KEY = "super-secret-brevo-key";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private BrevoEmailClient client;
    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new BrevoEmailClient(restTemplate, API_KEY, "noreply@example.com", "Distro");
        logger = (Logger) LoggerFactory.getLogger(BrevoEmailClient.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.ERROR);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logger.setLevel(previousLevel);
    }

    @Test
    void postsJsonToBrevoHttpApi() {
        server.expect(requestTo(BrevoEmailClient.ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", API_KEY))
                .andExpect(content().json("""
                        {
                          "sender": {"name": "Distro", "email": "noreply@example.com"},
                          "to": [{"email": "user@example.com"}],
                          "subject": "Your Distro verification code",
                          "htmlContent": "<p>Your code is 123456.</p>"
                        }
                        """))
                .andRespond(withSuccess("{\"messageId\":\"abc\"}", MediaType.APPLICATION_JSON));

        client.send("user@example.com", "Your Distro verification code", "Your code is 123456.");

        server.verify();
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(API_KEY));
    }

    @Test
    void logsStatusAndBodyWhenBrevoReturnsNon2xxWithoutTheKey() {
        server.expect(requestTo(BrevoEmailClient.ENDPOINT))
                .andRespond(withStatus(UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Key not found " + API_KEY + "\"}"));

        assertThatThrownBy(() -> client.send(
                "user@example.com", "Your Distro verification code", "Your code is 123456."))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessage("Email provider returned HTTP 401");

        assertThat(logs.list).anySatisfy(event -> {
            String message = event.getFormattedMessage();
            assertThat(message).contains("status=401");
            assertThat(message).contains("Key not found");
            assertThat(message).contains("[redacted]");
            assertThat(message).doesNotContain(API_KEY);
        });
        server.verify();
    }

    @Test
    void doesNotCallBrevoWhenNotConfigured() {
        BrevoEmailClient unconfigured = new BrevoEmailClient(restTemplate, "", "noreply@example.com", "Distro");

        assertThatThrownBy(() -> unconfigured.send("user@example.com", "Subject", "Body"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");

        server.verify();
    }
}
