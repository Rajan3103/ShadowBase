package com.shadowbase.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.User;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Exception.SandboxOperationException;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.Repository.UserRepository;
import com.shadowbase.backend.dto.ContainerResponse;
import com.shadowbase.backend.dto.CreateContainerRequest;
import com.shadowbase.backend.dto.CreateSandboxRequest;
import com.shadowbase.backend.dto.SandboxResponse;

@ExtendWith(MockitoExtension.class)
class SandboxServiceTest {

    @Mock
    private SandboxRepository sandboxRepository;

    @Mock
    private PostgresContainerService postgresContainerService;

    @Mock
private MySQLContainerService mySQLContainerService;

    @Mock
    private UserRepository userRepository;

    private SandboxService sandboxService;

    @BeforeEach
    void setUp() {
        sandboxService = new SandboxServiceImpl(
                sandboxRepository,
                postgresContainerService,
                mySQLContainerService,
                userRepository
        );
    }

    @Test
    void testCreateSandbox_SuccessWithContainer() {
        UUID userId = UUID.randomUUID();
        User mockUser = new User();
        mockUser.setId(userId);
        mockUser.setEmailId("test@shadowbase.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        UUID sandboxId = UUID.randomUUID();
        when(sandboxRepository.save(any(Sandbox.class))).thenAnswer(invocation -> {
            Sandbox s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(sandboxId);
            }
            return s;
        });

        Container mockContainer = Container.builder()
                .id("docker-container-123")
                .status(ContainerStatus.RUNNING)
                .databaseType(DatabaseType.POSTGRESQL)
                .databaseName("testdb")
                .port(5432)
                .build();

        when(postgresContainerService.createContainer(any(), any())).thenReturn(mockContainer);
        when(postgresContainerService.mapToResponse(any())).thenReturn(
                ContainerResponse.builder()
                        .id("docker-container-123")
                        .status(ContainerStatus.RUNNING)
                        .databaseType(DatabaseType.POSTGRESQL)
                        .port(5432)
                        .build()
        );

        CreateContainerRequest containerReq = CreateContainerRequest.builder()
                .databaseType(DatabaseType.POSTGRESQL)
                .databaseName("testdb")
                .build();

        CreateSandboxRequest request = CreateSandboxRequest.builder()
                .name("Dev Sandbox")
                .userId(userId)
                .initialContainers(List.of(containerReq))
                .build();

        SandboxResponse response = sandboxService.createSandbox(request);

        assertNotNull(response);
        assertEquals("Dev Sandbox", response.getName());
        assertEquals(SandboxStatus.READY, response.getStatus());
        assertEquals(1, response.getContainers().size());
        assertEquals(ContainerStatus.RUNNING, response.getContainers().get(0).getStatus());
    }

    @Test
    void testCreateSandbox_UserNotFound_ThrowsException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CreateSandboxRequest request = CreateSandboxRequest.builder()
                .name("Test Sandbox")
                .userId(userId)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> sandboxService.createSandbox(request));
    }

    @Test
    void testStartSandbox_Success() {
        UUID sandboxId = UUID.randomUUID();
        Container c1 = Container.builder().id("c1").status(ContainerStatus.STOPPED).build();
        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .name("My Sandbox")
                .status(SandboxStatus.STOPPED)
                .containers(new ArrayList<>(List.of(c1)))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));
        when(sandboxRepository.save(any(Sandbox.class))).thenAnswer(i -> i.getArgument(0));

        Container runningC1 = Container.builder().id("c1").status(ContainerStatus.RUNNING).build();
        when(postgresContainerService.startContainer("c1")).thenReturn(runningC1);
        c1.setStatus(ContainerStatus.RUNNING);

        SandboxResponse response = sandboxService.startSandbox(sandboxId);

        assertNotNull(response);
        assertEquals(SandboxStatus.READY, response.getStatus());
        verify(postgresContainerService, times(1)).startContainer("c1");
    }

    @Test
    void testStopSandbox_Success() {
        UUID sandboxId = UUID.randomUUID();
        Container c1 = Container.builder().id("c1").status(ContainerStatus.RUNNING).build();
        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .name("My Sandbox")
                .status(SandboxStatus.READY)
                .containers(new ArrayList<>(List.of(c1)))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));
        when(sandboxRepository.save(any(Sandbox.class))).thenAnswer(i -> i.getArgument(0));

        SandboxResponse response = sandboxService.stopSandbox(sandboxId);

        assertNotNull(response);
        assertEquals(SandboxStatus.STOPPED, response.getStatus());
        verify(postgresContainerService, times(1)).stopContainer("c1");
    }

    @Test
    void testDeleteSandbox_PreservesMetadataWithDestroyedStatus() {
        UUID sandboxId = UUID.randomUUID();
        Container c1 = Container.builder().id("c1").status(ContainerStatus.STOPPED).build();
        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .name("To Delete")
                .status(SandboxStatus.STOPPED)
                .containers(new ArrayList<>(List.of(c1)))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));
        when(sandboxRepository.save(any(Sandbox.class))).thenAnswer(i -> i.getArgument(0));

        sandboxService.deleteSandbox(sandboxId);

        assertEquals(SandboxStatus.DESTROYED, sandbox.getStatus());
        verify(postgresContainerService, times(1)).removeContainer("c1");
    }

    @Test
    void testStartSandbox_Destroyed_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .status(SandboxStatus.DESTROYED)
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));

        assertThrows(SandboxOperationException.class, () -> sandboxService.startSandbox(sandboxId));
    }

    @Test
    void testCreateSandbox_InitializesSchemaStatusToNotInitialized() {
        UUID userId = UUID.randomUUID();
        User mockUser = new User();
        mockUser.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        when(sandboxRepository.save(any(Sandbox.class))).thenAnswer(i -> {
            Sandbox s = i.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });

        CreateSandboxRequest req = CreateSandboxRequest.builder()
                .name("Schema Status Sandbox")
                .userId(userId)
                .build();

        SandboxResponse response = sandboxService.createSandbox(req);

        assertNotNull(response);
        assertEquals("Schema Status Sandbox", response.getName());
        assertEquals(com.shadowbase.backend.Entity.SchemaStatus.NOT_INITIALIZED, response.getSchemaStatus());
    }
}
