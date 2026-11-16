package com.shadowbase.backend.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.SchemaImport;
import com.shadowbase.backend.Entity.SchemaStatus;
import com.shadowbase.backend.Entity.User;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Exception.SchemaImportException;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.Repository.SchemaImportRepository;
import com.shadowbase.backend.dto.SchemaImportResponse;

@ExtendWith(MockitoExtension.class)
class SchemaServiceTest {

    @Mock
    private SandboxRepository sandboxRepository;

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private SchemaImportRepository schemaImportRepository;

    @Mock
    private ContainerConnectionProvider containerConnectionProvider;

    @Mock
    private SqlScriptExecutor sqlScriptExecutor;

    @Mock
    private Connection mockConnection;

    private SchemaService schemaService;

    @BeforeEach
    void setUp() {
        schemaService = new SchemaServiceImpl(
                sandboxRepository,
                containerRepository,
                schemaImportRepository,
                containerConnectionProvider,
                sqlScriptExecutor,
                10485760L // 10MB limit
        );
    }

    @Test
    void testImportSchema_Success() throws Exception {
        UUID sandboxId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);

        Container container = Container.builder()
                .id("c-100")
                .status(ContainerStatus.RUNNING)
                .databaseType(DatabaseType.POSTGRESQL)
                .databaseName("shadowbase_db")
                .build();

        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .name("Dev Sandbox")
                .user(user)
                .status(SandboxStatus.READY)
                .schemaStatus(SchemaStatus.NOT_INITIALIZED)
                .containers(new ArrayList<>(List.of(container)))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));
        when(schemaImportRepository.existsByContainerIdAndStatus("c-100", SchemaStatus.IN_PROGRESS)).thenReturn(false);
        when(containerConnectionProvider.isDatabaseReady(container)).thenReturn(true);
        when(containerConnectionProvider.openConnection(container)).thenReturn(mockConnection);

        when(schemaImportRepository.save(any(SchemaImport.class))).thenAnswer(i -> {
            SchemaImport si = i.getArgument(0);
            if (si.getId() == null) {
                si.setId(UUID.randomUUID());
            }
            return si;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "init.sql", "text/plain",
                "CREATE TABLE foo (id INT);".getBytes(StandardCharsets.UTF_8)
        );

        SchemaImportResponse response = schemaService.importSchema(sandboxId, file, userId);

        assertNotNull(response);
        assertEquals(SchemaStatus.SUCCESS, response.getStatus());
        assertEquals("init.sql", response.getFileName());
        assertEquals(DatabaseType.POSTGRESQL, response.getDatabaseType());
        assertEquals("c-100", response.getContainerId());
        assertNotNull(response.getChecksum());
        assertEquals(SchemaStatus.SUCCESS, sandbox.getSchemaStatus());

        verify(sqlScriptExecutor).execute(eq(mockConnection), any(InputStream.class), eq(DatabaseType.POSTGRESQL));
    }

    @Test
    void testImportSchema_EmptyFile_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        MockMultipartFile emptyFile = new MockMultipartFile("file", "init.sql", "text/plain", new byte[0]);

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> schemaService.importSchema(sandboxId, emptyFile, null)
        );

        assertTrue(ex.getMessage().contains("must not be null or empty"));
    }

    @Test
    void testImportSchema_InvalidExtension_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        MockMultipartFile badExtFile = new MockMultipartFile("file", "schema.txt", "text/plain", "SELECT 1;".getBytes());

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> schemaService.importSchema(sandboxId, badExtFile, null)
        );

        assertTrue(ex.getMessage().contains("Only plaintext '.sql' files are supported"));
    }

    @Test
    void testImportSchema_FileSizeExceeded_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        // Setup service with small 10 byte limit
        SchemaService smallLimitService = new SchemaServiceImpl(
                sandboxRepository, containerRepository, schemaImportRepository, containerConnectionProvider, sqlScriptExecutor, 10L);

        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.sql", "text/plain", "CREATE TABLE abcdefghijk (id INT);".getBytes());

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> smallLimitService.importSchema(sandboxId, largeFile, null)
        );

        assertTrue(ex.getMessage().contains("exceeds maximum allowable limit"));
    }

    @Test
    void testImportSchema_SandboxNotFound_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "init.sql", "text/plain", "SELECT 1;".getBytes());

        assertThrows(
                ResourceNotFoundException.class,
                () -> schemaService.importSchema(sandboxId, file, null)
        );
    }

    @Test
    void testImportSchema_DestroyedSandbox_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        Sandbox sandbox = Sandbox.builder().id(sandboxId).status(SandboxStatus.DESTROYED).build();
        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));

        MockMultipartFile file = new MockMultipartFile("file", "init.sql", "text/plain", "SELECT 1;".getBytes());

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> schemaService.importSchema(sandboxId, file, null)
        );

        assertTrue(ex.getMessage().contains("Cannot import schema into destroyed sandbox"));
    }

    @Test
    void testImportSchema_UnauthorizedUser_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID intruderId = UUID.randomUUID();

        User owner = new User();
        owner.setId(ownerId);

        Sandbox sandbox = Sandbox.builder().id(sandboxId).user(owner).status(SandboxStatus.READY).build();
        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));

        MockMultipartFile file = new MockMultipartFile("file", "init.sql", "text/plain", "SELECT 1;".getBytes());

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> schemaService.importSchema(sandboxId, file, intruderId)
        );

        assertTrue(ex.getMessage().contains("not authorized"));
    }

    @Test
    void testImportSchema_ImportAlreadyInProgress_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        Container container = Container.builder()
                .id("c-prog")
                .status(ContainerStatus.RUNNING)
                .build();

        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .status(SandboxStatus.READY)
                .containers(List.of(container))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));
        when(schemaImportRepository.existsByContainerIdAndStatus("c-prog", SchemaStatus.IN_PROGRESS)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile("file", "init.sql", "text/plain", "SELECT 1;".getBytes());

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> schemaService.importSchema(sandboxId, file, null)
        );

        assertTrue(ex.getMessage().contains("already in progress"));
    }

    @Test
    void testImportSchema_SchemaAlreadySuccess_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        Container container = Container.builder()
                .id("c-success")
                .status(ContainerStatus.RUNNING)
                .schemaStatus(SchemaStatus.SUCCESS)
                .build();

        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .status(SandboxStatus.READY)
                .schemaStatus(SchemaStatus.SUCCESS)
                .containers(List.of(container))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));

        MockMultipartFile file = new MockMultipartFile("file", "init.sql", "text/plain", "SELECT 1;".getBytes());

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> schemaService.importSchema(sandboxId, file, null)
        );

        assertTrue(ex.getMessage().contains("already been successfully initialized"));
    }

    @Test
    void testImportSchema_NoRunningContainer_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        Container stopped = Container.builder().id("c-stopped").status(ContainerStatus.STOPPED).build();
        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .status(SandboxStatus.READY)
                .containers(List.of(stopped))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));

        MockMultipartFile file = new MockMultipartFile("file", "init.sql", "text/plain", "SELECT 1;".getBytes());

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> schemaService.importSchema(sandboxId, file, null)
        );

        assertTrue(ex.getMessage().contains("No RUNNING database container found"));
    }

    @Test
    void testImportSchema_DatabaseNotReady_ThrowsException() {
        UUID sandboxId = UUID.randomUUID();
        Container container = Container.builder()
                .id("c-notready")
                .status(ContainerStatus.RUNNING)
                .databaseType(DatabaseType.POSTGRESQL)
                .build();

        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .status(SandboxStatus.READY)
                .containers(List.of(container))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));
        when(schemaImportRepository.existsByContainerIdAndStatus("c-notready", SchemaStatus.IN_PROGRESS)).thenReturn(false);
        when(containerConnectionProvider.isDatabaseReady(container)).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile("file", "init.sql", "text/plain", "SELECT 1;".getBytes());

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> schemaService.importSchema(sandboxId, file, null)
        );

        assertTrue(ex.getMessage().contains("not ready to accept connections"));
    }

    @Test
    void testImportSchema_ExecutionFailure_UpdatesStatusToFailed() throws Exception {
        UUID sandboxId = UUID.randomUUID();
        Container container = Container.builder()
                .id("c-fail")
                .status(ContainerStatus.RUNNING)
                .databaseType(DatabaseType.MYSQL)
                .build();

        Sandbox sandbox = Sandbox.builder()
                .id(sandboxId)
                .status(SandboxStatus.READY)
                .containers(List.of(container))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));
        when(schemaImportRepository.existsByContainerIdAndStatus("c-fail", SchemaStatus.IN_PROGRESS)).thenReturn(false);
        when(containerConnectionProvider.isDatabaseReady(container)).thenReturn(true);
        when(containerConnectionProvider.openConnection(container)).thenReturn(mockConnection);

        when(schemaImportRepository.save(any(SchemaImport.class))).thenAnswer(i -> i.getArgument(0));

        doThrow(new SchemaImportException("Syntax error at line 5"))
                .when(sqlScriptExecutor).execute(any(), any(), any());

        MockMultipartFile file = new MockMultipartFile("file", "bad.sql", "text/plain", "BAD SQL;".getBytes());

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> schemaService.importSchema(sandboxId, file, null)
        );

        assertTrue(ex.getMessage().contains("Syntax error at line 5"));
        assertEquals(SchemaStatus.FAILED, sandbox.getSchemaStatus());
    }

    @Test
    void testGetLatestImportStatus_Success() {
        UUID sandboxId = UUID.randomUUID();
        when(sandboxRepository.existsById(sandboxId)).thenReturn(true);

        SchemaImport record = SchemaImport.builder()
                .id(UUID.randomUUID())
                .containerId("c-1")
                .fileName("schema.sql")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(SchemaStatus.SUCCESS)
                .startedAt(LocalDateTime.now())
                .build();

        when(schemaImportRepository.findFirstBySandboxIdOrderByStartedAtDesc(sandboxId)).thenReturn(Optional.of(record));

        SchemaImportResponse res = schemaService.getLatestImportStatus(sandboxId);
        assertNotNull(res);
        assertEquals(SchemaStatus.SUCCESS, res.getStatus());
        assertEquals("schema.sql", res.getFileName());
    }

    @Test
    void testGetImportHistory_Success() {
        UUID sandboxId = UUID.randomUUID();
        when(sandboxRepository.existsById(sandboxId)).thenReturn(true);

        SchemaImport r1 = SchemaImport.builder()
                .id(UUID.randomUUID())
                .containerId("c-1")
                .fileName("schema1.sql")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(SchemaStatus.SUCCESS)
                .startedAt(LocalDateTime.now())
                .build();

        when(schemaImportRepository.findBySandboxIdOrderByStartedAtDesc(sandboxId)).thenReturn(List.of(r1));

        List<SchemaImportResponse> history = schemaService.getImportHistory(sandboxId);
        assertEquals(1, history.size());
        assertEquals("schema1.sql", history.get(0).getFileName());
    }
}
