package com.example.distrobackend.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Base for tests that need the real schema: a throwaway Postgres process is
 * started once per JVM (no Docker, nothing installed), Flyway applies every
 * migration to it, and Hibernate validates the entities against the result.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=aW50ZWdyYXRpb24tdGVzdC1zZWNyZXQtMDEyMzQ1Njc4OS1hYmNkZWZnaGlqa2xtbm9w",
        "paystack.enabled=false"
})
public abstract class AbstractPostgresIntegrationTest {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not start the embedded Postgres used by integration tests", ex);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }
}