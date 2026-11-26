package com.example.distrobackend.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentEnvironmentDiagnosticsTest {

    @Test
    void reportsBaseVariablesAndOnlyRequirementsForEnabledIntegrations() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("RENDER", "true")
                .withProperty("PAYSTACK_ENABLED", "true")
                .withProperty("PAYSTACK_TRANSFERS_ENABLED", "true")
                .withProperty("PAYSTACK_MODE", "test")
                .withProperty("PAYSTACK_TEST_AUTO_SETTLEMENT_ENABLED", "true");

        DeploymentEnvironmentDiagnostics.Report report =
                DeploymentEnvironmentDiagnostics.inspect(environment);

        assertThat(report.requiredMissing()).containsExactly("JWT_SECRET", "DATABASE_URL");
        assertThat(report.enabledFeatureMissing()).containsExactly(
                "PAYSTACK_SECRET_KEY", "PAYSTACK_WEBHOOK_SECRET");
        assertThat(report.optionalFeatureMissing()).singleElement()
                .asString().contains("MPESA_CONSUMER_KEY", "MPESA_CHECKOUT_CALLBACK_URL");
    }

    @Test
    void doesNotRequireProviderSecretsWhenIntegrationsAreDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_SECRET", "present")
                .withProperty("PAYSTACK_ENABLED", "false")
                .withProperty("PAYSTACK_TRANSFERS_ENABLED", "false");

        DeploymentEnvironmentDiagnostics.Report report =
                DeploymentEnvironmentDiagnostics.inspect(environment);

        assertThat(report.requiredMissing()).isEmpty();
        assertThat(report.enabledFeatureMissing()).isEmpty();
        assertThat(report.optionalFeatureMissing()).isEmpty();
        assertThat(report.isReady()).isTrue();
    }

    @Test
    void reportsModeMismatchWithoutLoggingEitherSecretValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("JWT_SECRET", "present")
                .withProperty("PAYSTACK_ENABLED", "true")
                .withProperty("PAYSTACK_MODE", "live")
                .withProperty("PAYSTACK_SECRET_KEY", "sk_test_placeholder");

        DeploymentEnvironmentDiagnostics.Report report =
                DeploymentEnvironmentDiagnostics.inspect(environment);

        assertThat(report.enabledFeatureMissing())
                .contains("PAYSTACK_WEBHOOK_SECRET")
                .anyMatch(issue -> issue.startsWith("PAYSTACK_MODE"));
        assertThat(report.toString()).doesNotContain("sk_test_placeholder");
    }
}
