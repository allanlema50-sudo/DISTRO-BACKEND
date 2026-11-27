package com.example.distrobackend;

import com.example.distrobackend.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;

/** Loads the whole application on a real Postgres: every migration runs and every entity is validated. */
class DistroBackendApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
    }

}
