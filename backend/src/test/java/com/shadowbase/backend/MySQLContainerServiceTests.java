
package com.shadowbase.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Exception.ContainerOperationException;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;

@ExtendWith(MockitoExtension.class)
class MySQLContainerServiceTests {

    @Mock
    private ContainerRepository containerRepository;

    private MySQLContainerService service;

    @BeforeEach
    void setUp() {
        service = new MySQLContainerService(containerRepository);
    }

    @Test
    void shouldRetrieveMySQLContainer() {

        Container container = Container.builder()
                .id("mysql-1")
                .databaseType(DatabaseType.MYSQL)
                .databaseName("testdb")
                .status(ContainerStatus.RUNNING)
                .port(3306)
                .build();

        when(containerRepository.findById("mysql-1"))
                .thenReturn(Optional.of(container));

        Container result = service.getContainer("mysql-1");

        assertNotNull(result);
        assertEquals("mysql-1", result.getId());
        assertEquals(DatabaseType.MYSQL, result.getDatabaseType());
        assertEquals(ContainerStatus.RUNNING, result.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenContainerIsNotMySQL() {

        Container container = Container.builder()
                .id("postgres-1")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(ContainerStatus.RUNNING)
                .build();

        when(containerRepository.findById("postgres-1"))
                .thenReturn(Optional.of(container));

        assertThrows(
                ContainerOperationException.class,
                () -> service.getContainer("postgres-1")
        );
    }

    @Test
    void shouldThrowExceptionWhenContainerDoesNotExist() {

        when(containerRepository.findById("missing"))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.getContainer("missing")
        );
    }

    @Test
    void shouldReturnOnlyMySQLContainersForSandbox() {

        UUID sandboxId = UUID.randomUUID();

        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .build();

        Container mysql = Container.builder()
                .id("mysql-1")
                .sandbox(sandbox)
                .databaseType(DatabaseType.MYSQL)
                .status(ContainerStatus.RUNNING)
                .build();

        Container postgres = Container.builder()
                .id("postgres-1")
                .sandbox(sandbox)
                .databaseType(DatabaseType.POSTGRESQL)
                .status(ContainerStatus.RUNNING)
                .build();

        when(containerRepository.findBySandboxId(sandboxId))
                .thenReturn(List.of(mysql, postgres));

        List<Container> result =
                service.getContainersBySandbox(sandboxId);

        assertEquals(1, result.size());
        assertEquals("mysql-1", result.get(0).getId());
        assertEquals(DatabaseType.MYSQL, result.get(0).getDatabaseType());
    }
}
