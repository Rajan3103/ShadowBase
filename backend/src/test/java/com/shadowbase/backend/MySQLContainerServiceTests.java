
package com.shadowbase.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.shadowbase.backend.dto.EnvironmentResponse;
import com.shadowbase.backend.service.MySQLContainerService;

class MySQLContainerServiceTests {

    @Test
    void shouldCreateRetrieveAndStopEnvironment() {

        MySQLContainerService service = new MySQLContainerService();

        EnvironmentResponse environment = service.createEnvironment();

        try {
            assertNotNull(environment.id());
            assertEquals("RUNNING", environment.status());
            assertNotNull(environment.jdbcUrl());
            assertNotNull(environment.username());
            assertNotNull(environment.password());

            EnvironmentResponse retrieved =
                    service.getEnvironment(environment.id());

            assertNotNull(retrieved);
            assertEquals(environment.id(), retrieved.id());

            assertTrue(service.stopEnvironment(environment.id()));

            assertNull(service.getEnvironment(environment.id()));
            assertFalse(service.stopEnvironment(environment.id()));

        } finally {
            service.stopEnvironment(environment.id());
        }
    }
}
