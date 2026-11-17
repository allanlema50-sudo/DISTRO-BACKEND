package com.example.distrobackend.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailProviderConfigurationTest {

    @Test
    void explicitProviderWins() {
        assertThat(EmailProviderConfiguration.resolve(" Brevo ", "mailgun")).isEqualTo("brevo");
        assertThat(EmailProviderConfiguration.resolve("log", "http")).isEqualTo("log");
    }

    @Test
    void unsetProviderFollowsBrevoOtpOtherwiseMailgun() {
        assertThat(EmailProviderConfiguration.resolve(null, "brevo")).isEqualTo("brevo");
        assertThat(EmailProviderConfiguration.resolve("  ", "http")).isEqualTo("mailgun");
        assertThat(EmailProviderConfiguration.resolve(null, "mailgun")).isEqualTo("mailgun");
    }

    @Test
    void logProviderIsRejectedOutsideTheLocalProfile() {
        assertThatThrownBy(() -> EmailProviderConfiguration.assertUsable("log", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local");

        EmailProviderConfiguration.assertUsable("log", true);
        EmailProviderConfiguration.assertUsable("brevo", false);
    }

    @Test
    void unknownProviderIsRejected() {
        assertThatThrownBy(() -> EmailProviderConfiguration.assertUsable("smtp", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mailgun, brevo, or log");
    }
}
