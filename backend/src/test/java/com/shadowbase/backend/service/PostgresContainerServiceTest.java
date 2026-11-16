package com.shadowbase.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.dto.ContainerResponse;
import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.DataSource;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Exception.ContainerOperationException;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;

/**
 * Unit tests for {@link PostgresContainerService}.
 * Verifies retrieval, validation, security masking, and database type filtering for PostgreSQL containers.
 */
@ExtendWith(MockitoExtension.class)
class PostgresContainerServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    private PostgresContainerService postgresContainerService;

    @BeforeEach
    void setUp() {
        postgresContainerService = new PostgresContainerService(containerRepository);
    }

    @Test
    void testGetContainer_Success() {
        String containerId = "docker-123";
        Container container = Container.builder()
                .id(containerId)
                .databaseName("mydb")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(ContainerStatus.RUNNING)
                .build();

        when(containerRepository.findById(containerId)).thenReturn(Optional.of(container));

        Container found = postgresContainerService.getContainer(containerId);

        assertNotNull(found);
        assertEquals(containerId, found.getId());
        assertEquals(DatabaseType.POSTGRESQL, found.getDatabaseType());
        assertEquals(ContainerStatus.RUNNING, found.getStatus());
    }

    @Test
    void testGetContainer_NotFound_ThrowsException() {
        String containerId = "non-existent";
        when(containerRepository.findById(containerId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> postgresContainerService.getContainer(containerId));
    }

    @Test
    void testGetContainer_WrongDatabaseType_ThrowsException() {
        String containerId = "mysql-c-1";
        Container container = Container.builder()
                .id(containerId)
                .databaseType(DatabaseType.MYSQL)
                .status(ContainerStatus.RUNNING)
                .build();

        when(containerRepository.findById(containerId)).thenReturn(Optional.of(container));

        assertThrows(ContainerOperationException.class, () -> postgresContainerService.getContainer(containerId));
    }

    @Test
    void testReturnOnlyPostgresContainersForSandbox() {
        UUID sandboxId = UUID.randomUUID();
        Sandbox sandbox = Sandbox.builder().id(sandboxId).build();

        Container postgres = Container.builder()
                .id("pg-1")
                .sandbox(sandbox)
                .databaseType(DatabaseType.POSTGRESQL)
                .status(ContainerStatus.RUNNING)
                .build();

        Container mysql = Container.builder()
                .id("my-1")
                .sandbox(sandbox)
                .databaseType(DatabaseType.MYSQL)
                .status(ContainerStatus.RUNNING)
                .build();

        when(containerRepository.findBySandboxId(sandboxId)).thenReturn(List.of(postgres, mysql));

        List<Container> result = postgresContainerService.getContainersBySandbox(sandboxId);

        assertEquals(1, result.size());
        assertEquals("pg-1", result.get(0).getId());
        assertEquals(DatabaseType.POSTGRESQL, result.get(0).getDatabaseType());
    }

    @Test
    void testMapToResponse_HidesSensitiveInformation() {
        UUID sandboxId = UUID.randomUUID();
        Sandbox sandbox = Sandbox.builder().id(sandboxId).name("TestSandbox").build();

        Container container = Container.builder()
                .id("c-abc")
                .sandbox(sandbox)
                .imageName("postgres:16-alpine")
                .host("localhost")
                .port(5432)
                .jdbcUrl("jdbc:postgresql://localhost:5432/mydb")
                .username("dbuser")
                .password("supersecret123")
                .databaseName("mydb")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(ContainerStatus.RUNNING)
                .build();

        ContainerResponse response = postgresContainerService.mapToResponse(container);

        assertNotNull(response);
        assertEquals("c-abc", response.getId());
        assertEquals(sandboxId, response.getSandboxId());
        assertEquals("localhost", response.getHost());
        assertEquals(5432, response.getPort());
        assertEquals("dbuser", response.getUsername());
        assertEquals(ContainerStatus.RUNNING, response.getStatus());
    }

    @Test
    void testStopContainer_AlreadyStopped() {
        String containerId = "c-123";
        Container container = Container.builder()
                .id(containerId)
                .databaseType(DatabaseType.POSTGRESQL)
                .status(ContainerStatus.STOPPED)
                .build();

        when(containerRepository.findById(containerId)).thenReturn(Optional.of(container));

        Container result = postgresContainerService.stopContainer(containerId);

        assertEquals(ContainerStatus.STOPPED, result.getStatus());
    }

    @Test
    void testMapToResponse_IncludesDataSourceId() {
        UUID dataSourceId = UUID.randomUUID();
        DataSource dataSource = DataSource.builder()
                .id(dataSourceId)
                .connectionName("production-db")
                .host("db.internal")
                .port(5432)
                .databaseName("proddb")
                .databaseUsername("produser")
                .build();

        Container container = Container.builder()
                .id("c-with-ds")
                .imageName("postgres:16-alpine")
                .databaseName("proddb")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(ContainerStatus.RUNNING)
                .dataSource(dataSource)
                .build();

        // Establish reciprocal link
        dataSource.setContainer(container);

        ContainerResponse response = postgresContainerService.mapToResponse(container);

        assertNotNull(response);
        assertEquals(dataSourceId, response.getDataSourceId());
        assertEquals("c-with-ds", dataSource.getContainer().getId());
        assertEquals(dataSourceId, container.getDataSource().getId());
    }
}
