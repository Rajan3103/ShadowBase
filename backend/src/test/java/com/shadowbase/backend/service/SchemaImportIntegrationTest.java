package com.shadowbase.backend.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.SchemaStatus;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.dto.SchemaImportResponse;

import org.springframework.context.annotation.Import;
import com.shadowbase.backend.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@EnabledIf("isDockerAvailable")
class SchemaImportIntegrationTest {

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private SandboxRepository sandboxRepository;

    @Autowired
    private ContainerRepository containerRepository;

    @Autowired
    private ContainerConnectionProvider connectionProvider;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @EnabledIf("isDockerAvailable")
    void testRealPostgreSQLSchemaImport() throws Exception {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")) {

            postgres.start();

            // Setup Sandbox
            Sandbox sandbox = Sandbox.builder()
                    .name("Integration Sandbox")
                    .status(SandboxStatus.READY)
                    .schemaStatus(SchemaStatus.NOT_INITIALIZED)
                    .build();
            sandbox = sandboxRepository.save(sandbox);

            // Setup Container pointing to live Testcontainer
            Container container = Container.builder()
                    .id(postgres.getContainerId())
                    .sandbox(sandbox)
                    .databaseType(DatabaseType.POSTGRESQL)
                    .databaseName("testdb")
                    .username("testuser")
                    .password("testpass")
                    .host(postgres.getHost())
                    .port(postgres.getFirstMappedPort())
                    .jdbcUrl(postgres.getJdbcUrl())
                    .imageName("postgres:16-alpine")
                    .status(ContainerStatus.RUNNING)
                    .build();
            containerRepository.save(container);
            sandbox.getContainers().add(container);
            sandboxRepository.save(sandbox);

            // Sample schema containing customers table
            String sqlContent = "CREATE TABLE customers (\n" +
                    "    id BIGINT PRIMARY KEY,\n" +
                    "    name VARCHAR(100) NOT NULL,\n" +
                    "    email VARCHAR(150) UNIQUE\n" +
                    ");\n" +
                    "INSERT INTO customers (id, name, email) VALUES (1, 'Alice Smith', 'alice@example.com');\n";

            MockMultipartFile file = new MockMultipartFile(
                    "file", "customers_schema.sql", "text/plain", sqlContent.getBytes()
            );

            // Execute import
            SchemaImportResponse response = schemaService.importSchema(sandbox.getId(), file, null);

            assertNotNull(response);
            assertEquals(SchemaStatus.SUCCESS, response.getStatus());
            assertEquals("customers_schema.sql", response.getFileName());

            // Verify table actually exists in the real PostgreSQL container
            try (Connection conn = connectionProvider.openConnection(container);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name, email FROM customers WHERE id = 1")) {

                assertTrue(rs.next(), "Customer row should exist in database");
                assertEquals("Alice Smith", rs.getString("name"));
                assertEquals("alice@example.com", rs.getString("email"));
            }
        }
    }
}
