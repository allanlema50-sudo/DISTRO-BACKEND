package com.example.distrobackend.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionDemoFlagGuardTest {

    @Test
    void prodRefusesCustomerFulfillmentDemoFlag() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.demo.customer-fulfillment.enabled", "true");
        environment.setActiveProfiles("prod");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        assertThatThrownBy(() -> new ProductionDemoFlagGuard().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.demo.customer-fulfillment.enabled")
                .hasMessageContaining("Production refuses");
    }

    @Test
    void prodRefusesDemoPaymentsFlag() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("logiflow.demo-payments.enabled", "true");
        environment.setActiveProfiles("prod");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        assertThatThrownBy(() -> new ProductionDemoFlagGuard().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("logiflow.demo-payments.enabled");
    }

    @Test
    void prodRefusesWhenBothDemoFlagsAreTrue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.demo.customer-fulfillment.enabled", "true")
                .withProperty("logiflow.demo-payments.enabled", "true");
        environment.setActiveProfiles("prod");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        assertThatThrownBy(() -> new ProductionDemoFlagGuard().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.demo.customer-fulfillment.enabled")
                .hasMessageContaining("logiflow.demo-payments.enabled");
    }

    @Test
    void prodStartsWhenAllDemoFlagsAreFalse() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.demo.customer-fulfillment.enabled", "false")
                .withProperty("logiflow.demo-payments.enabled", "false");
        environment.setActiveProfiles("prod");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        new ProductionDemoFlagGuard().initialize(context);
        assertThat(ProductionDemoFlagGuard.enabledDemoFlags(environment)).isEmpty();
    }

    @Test
    void nonProdAllowsDemoFlags() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.demo.customer-fulfillment.enabled", "true")
                .withProperty("logiflow.demo-payments.enabled", "true");
        environment.setActiveProfiles("local");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        new ProductionDemoFlagGuard().initialize(context);
        assertThat(ProductionDemoFlagGuard.enabledDemoFlags(environment))
                .containsExactly(
                        "app.demo.customer-fulfillment.enabled",
                        "logiflow.demo-payments.enabled");
    }

    @Test
    void defaultsAreTreatedAsDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);

        new ProductionDemoFlagGuard().initialize(context);
        assertThat(ProductionDemoFlagGuard.enabledDemoFlags(environment)).isEmpty();
    }
}
