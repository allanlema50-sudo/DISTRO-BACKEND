package com.example.distrobackend.configuration;

import com.example.distrobackend.integration.notification.BrevoEmailClient;
import com.example.distrobackend.integration.notification.EmailProviderClient;
import com.example.distrobackend.integration.notification.LoggingEmailClient;
import com.example.distrobackend.integration.notification.MailgunEmailClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;
import java.util.Set;

@Configuration
public class EmailProviderConfiguration {

    static final Set<String> PROVIDERS = Set.of("mailgun", "brevo", "log");

    @Bean
    public EmailProviderClient emailProviderClient(
            Environment environment,
            @Qualifier("darajaRestTemplate") RestTemplate restTemplate) {
        String provider = resolve(
                environment.getProperty("email.provider"),
                environment.getProperty("otp.provider"));
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        assertUsable(provider, local);
        return switch (provider) {
            case "log" -> new LoggingEmailClient();
            case "brevo" -> new BrevoEmailClient(
                    restTemplate,
                    environment.getProperty("brevo.api-key", ""),
                    environment.getProperty("brevo.sender.email", ""),
                    environment.getProperty("brevo.sender.name", ""));
            case "mailgun" -> new MailgunEmailClient(
                    restTemplate,
                    environment.getProperty("mailgun.api.key", ""),
                    environment.getProperty("mailgun.domain", ""),
                    environment.getProperty("mailgun.api.base-url", "https://api.mailgun.net"));
            default -> throw new IllegalStateException("EMAIL_PROVIDER must be mailgun, brevo, or log");
        };
    }

    /**
     * An explicit EMAIL_PROVIDER wins. When it is unset, OTP_PROVIDER=brevo selects
     * Brevo; every other OTP provider keeps the existing Mailgun default.
     */
    static String resolve(String emailProvider, String otpProvider) {
        if (emailProvider != null && !emailProvider.isBlank()) {
            return emailProvider.trim().toLowerCase(Locale.ROOT);
        }
        if (otpProvider != null && "brevo".equalsIgnoreCase(otpProvider.trim())) {
            return "brevo";
        }
        return "mailgun";
    }

    static void assertUsable(String provider, boolean localProfile) {
        if (!PROVIDERS.contains(provider)) {
            throw new IllegalStateException("EMAIL_PROVIDER must be mailgun, brevo, or log");
        }
        if ("log".equals(provider) && !localProfile) {
            throw new IllegalStateException(
                    "EMAIL_PROVIDER=log is only allowed when the local Spring profile is active");
        }
    }
}
