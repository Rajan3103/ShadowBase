package com.shadowbase.backend;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import com.shadowbase.backend.service.MySQLContainerService;

class MySQLContainerServiceTests {

    @Test
    void shouldStartMySQLContainer() {

        MySQLContainer container =
                new MySQLContainerService().createContainer();

        try {
            assertTrue(container.isRunning());
            assertNotNull(container.getJdbcUrl());

        } finally {
            container.stop();
        }
    }
}