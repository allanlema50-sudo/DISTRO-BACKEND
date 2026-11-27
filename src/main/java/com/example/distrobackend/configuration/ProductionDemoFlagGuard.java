package com.example.distrobackend.configuration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.ArrayList;
import java.util.List;

/**
 * Production must never start with demo or simulated payment behaviour enabled.
 * Mock code may remain in the tree, but these flags must stay false under the
 * {@code prod} profile.
 */
public class ProductionDemoFlagGuard
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    static final String CUSTOMER_FULFILLMENT = "app.demo.customer-fulfillment.enabled";
    static final String DEMO_PAYMENTS = "logiflow.demo-payments.enabled";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> enabled = enabledDemoFlags(environment);
        if (!enabled.isEmpty()) {
            throw new IllegalStateException(
                    "Production refuses to start with demo/simulator flags enabled: "
                            + String.join(", ", enabled)
                            + ". Set them to false (APP_DEMO_CUSTOMER_FULFILLMENT=false, "
                            + "LOGIFLOW_DEMO_PAYMENTS_ENABLED=false).");
        }
    }

    static List<String> enabledDemoFlags(Environment environment) {
        List<String> enabled = new ArrayList<>();
        if (isTrue(environment, CUSTOMER_FULFILLMENT)
                || isTrue(environment, "APP_DEMO_CUSTOMER_FULFILLMENT")) {
            enabled.add(CUSTOMER_FULFILLMENT);
        }
        if (isTrue(environment, DEMO_PAYMENTS)
                || isTrue(environment, "LOGIFLOW_DEMO_PAYMENTS_ENABLED")) {
            enabled.add(DEMO_PAYMENTS);
        }
        return List.copyOf(enabled);
    }

    private static boolean isTrue(Environment environment, String key) {
        return Boolean.parseBoolean(environment.getProperty(key, "false"));
    }
}
