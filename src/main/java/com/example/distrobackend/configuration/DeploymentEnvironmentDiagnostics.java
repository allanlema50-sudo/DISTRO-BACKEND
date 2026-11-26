package com.example.distrobackend.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/** Logs missing environment-variable names early, without exposing configured values. */
@Slf4j
public class DeploymentEnvironmentDiagnostics
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Report report = inspect(applicationContext.getEnvironment());
        if (!report.requiredMissing().isEmpty()) {
            log.warn("Required deployment configuration is missing: {}", report.requiredMissing());
        }
        if (!report.enabledFeatureMissing().isEmpty()) {
            log.warn("Enabled integrations are missing configuration: {}", report.enabledFeatureMissing());
        }
        if (!report.optionalFeatureMissing().isEmpty()) {
            log.info("Optional integrations are not fully configured (only needed if used): {}",
                    report.optionalFeatureMissing());
        }
        if (report.isReady()) {
            log.info("Deployment environment check passed; no missing required configuration was detected.");
        }
    }

    static Report inspect(Environment environment) {
        List<String> requiredMissing = new ArrayList<>();
        List<String> enabledFeatureMissing = new ArrayList<>();
        List<String> optionalFeatureMissing = new ArrayList<>();

        addIfBlank(environment, requiredMissing, "JWT_SECRET");

        boolean render = Boolean.parseBoolean(environment.getProperty("RENDER", "false"))
                || hasValue(environment, "RENDER_SERVICE_ID")
                || hasValue(environment, "PORT");
        if (render) addIfBlank(environment, requiredMissing, "DATABASE_URL");

        boolean paystackEnabled = Boolean.parseBoolean(environment.getProperty("PAYSTACK_ENABLED", "false"));
        boolean transfersEnabled = Boolean.parseBoolean(
                environment.getProperty("PAYSTACK_TRANSFERS_ENABLED", "false"));
        if (paystackEnabled) {
            addIfBlank(environment, enabledFeatureMissing, "PAYSTACK_SECRET_KEY");
            addIfBlank(environment, enabledFeatureMissing, "PAYSTACK_WEBHOOK_SECRET");
            String mode = environment.getProperty("PAYSTACK_MODE", "test").trim();
            String key = environment.getProperty("PAYSTACK_SECRET_KEY", "").trim();
            String expectedPrefix = "live".equalsIgnoreCase(mode) ? "sk_live_"
                    : "test".equalsIgnoreCase(mode) ? "sk_test_" : null;
            if (expectedPrefix == null || (!key.isBlank() && !key.startsWith(expectedPrefix))) {
                enabledFeatureMissing.add("PAYSTACK_MODE (must match the PAYSTACK_SECRET_KEY environment)");
            }
        }
        if (transfersEnabled && !paystackEnabled) {
            enabledFeatureMissing.add("PAYSTACK_ENABLED (must be true when PAYSTACK_TRANSFERS_ENABLED=true)");
        }

        boolean automaticTestSettlement = Boolean.parseBoolean(
                environment.getProperty("PAYSTACK_TEST_AUTO_SETTLEMENT_ENABLED", "false"));
        if (automaticTestSettlement) {
            if (!paystackEnabled) enabledFeatureMissing.add("PAYSTACK_ENABLED=true");
            if (!transfersEnabled) enabledFeatureMissing.add("PAYSTACK_TRANSFERS_ENABLED=true");
            if (!"test".equalsIgnoreCase(environment.getProperty("PAYSTACK_MODE", "test").trim())) {
                enabledFeatureMissing.add("PAYSTACK_MODE=test");
            }
        }

        if (render) {
            List<String> darajaMissing = missing(environment,
                    "MPESA_CONSUMER_KEY", "MPESA_CONSUMER_SECRET", "MPESA_SHORTCODE", "MPESA_PASSKEY",
                    "MPESA_CHECKOUT_CALLBACK_URL", "MPESA_CALLBACK_SECRET");
            if (!darajaMissing.isEmpty()) {
                optionalFeatureMissing.add("Daraja checkout: " + String.join(", ", darajaMissing));
            }

            if ("prod".equalsIgnoreCase(environment.getProperty("SPRING_PROFILES_ACTIVE", ""))
                    && "http".equalsIgnoreCase(environment.getProperty("OTP_PROVIDER", "http"))) {
                addIfBlank(environment, enabledFeatureMissing, "OTP_SMS_ENDPOINT");
                addIfBlank(environment, enabledFeatureMissing, "OTP_SMS_API_KEY");
            }
        }

        if (Boolean.parseBoolean(environment.getProperty("MAIL_ENABLED", "false"))) {
            addIfBlank(environment, enabledFeatureMissing, "MAIL_HOST");
            addIfBlank(environment, enabledFeatureMissing, "GMAIL_SMTP_USERNAME");
            addIfBlank(environment, enabledFeatureMissing, "GMAIL_SMTP_APP_PASSWORD");
        }

        return new Report(List.copyOf(requiredMissing), List.copyOf(enabledFeatureMissing),
                List.copyOf(optionalFeatureMissing));
    }

    private static List<String> missing(Environment environment, String... names) {
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            if (!hasValue(environment, name)) missing.add(name);
        }
        return missing;
    }

    private static void addIfBlank(Environment environment, List<String> target, String name) {
        if (!hasValue(environment, name)) target.add(name);
    }

    private static boolean hasValue(Environment environment, String name) {
        String value = environment.getProperty(name);
        return value != null && !value.isBlank();
    }

    record Report(List<String> requiredMissing, List<String> enabledFeatureMissing,
                  List<String> optionalFeatureMissing) {
        boolean isReady() {
            return requiredMissing.isEmpty() && enabledFeatureMissing.isEmpty()
                    && optionalFeatureMissing.isEmpty();
        }
    }
}
