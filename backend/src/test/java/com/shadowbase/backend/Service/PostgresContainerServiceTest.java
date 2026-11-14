package com.shadowbase.backend.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.Dto.ContainerResponse;
import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;

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
        assertEquals(ContainerStatus.RUNNING, found.getStatus());
    }

    @Test
    void testGetContainer_NotFound_ThrowsException() {
        String containerId = "non-existent";
        when(containerRepository.findById(containerId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> postgresContainerService.getContainer(containerId));
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
                .status(ContainerStatus.STOPPED)
                .build();

        when(containerRepository.findById(containerId)).thenReturn(Optional.of(container));

        Container result = postgresContainerService.stopContainer(containerId);

        assertEquals(ContainerStatus.STOPPED, result.getStatus());
    }
}
