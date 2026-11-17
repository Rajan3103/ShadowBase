package com.shadowbase.backend.service.seeding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.shadowbase.backend.Entity.ConsistencyMode;
import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.DataSource;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.SchemaStatus;
import com.shadowbase.backend.Entity.SeedJob;
import com.shadowbase.backend.Entity.SeedJobStatus;
import com.shadowbase.backend.Entity.SeedingMode;
import com.shadowbase.backend.Entity.User;
import com.shadowbase.backend.Exception.DataSeedingException;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.Repository.SeedJobRepository;
import com.shadowbase.backend.Repository.SeedTableProgressRepository;
import com.shadowbase.backend.dto.DataSeedRequest;
import com.shadowbase.backend.dto.DataSeedValidationResponse;
import com.shadowbase.backend.dto.SeedJobResponse;
import com.shadowbase.backend.service.ContainerConnectionProvider;
import com.shadowbase.backend.service.adapter.DatabaseDialectAdapter;
import com.shadowbase.backend.service.adapter.DatabaseDialectAdapterFactory;

/**
 * Comprehensive unit tests for {@link DataSeedingServiceImpl}.
 * Verifies sandbox authorization, schema readiness, duplicate prevention, preflight validation, and cancellation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSeedingServiceTest {

    @Mock
    private SandboxRepository sandboxRepository;

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private SeedJobRepository seedJobRepository;

    @Mock
    private SeedTableProgressRepository tableProgressRepository;

    @Mock
    private SourceConnectionProvider sourceConnectionProvider;

    @Mock
    private ContainerConnectionProvider containerConnectionProvider;

    @Mock
    private SchemaInspector schemaInspector;

    @Mock
    private TableDependencyPlanner dependencyPlanner;

    @Mock
    private DatabaseDialectAdapterFactory dialectAdapterFactory;

    @Mock
    private SeedJobWorker seedJobWorker;

    @Mock
    private DatabaseDialectAdapter dialectAdapter;

    private DataSeedingServiceImpl service;

    private UUID sandboxId;
    private Sandbox sandbox;
    private Container container;
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        service = new DataSeedingServiceImpl(
                sandboxRepository,
                containerRepository,
                seedJobRepository,
                tableProgressRepository,
                sourceConnectionProvider,
                containerConnectionProvider,
                schemaInspector,
                dependencyPlanner,
                dialectAdapterFactory,
                seedJobWorker
        );

        sandboxId = UUID.randomUUID();
        dataSource = DataSource.builder()
                .id(UUID.randomUUID())
                .connectionName("source-db")
                .host("localhost")
                .port(5432)
                .databaseName("proddb")
                .databaseUsername("produser")
                .build();

        container = Container.builder()
                .id("c-100")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(ContainerStatus.RUNNING)
                .schemaStatus(SchemaStatus.SUCCESS)
                .dataSource(dataSource)
                .build();

        sandbox = Sandbox.builder()
                .id(sandboxId)
                .status(SandboxStatus.READY)
                .schemaStatus(SchemaStatus.SUCCESS)
                .containers(new ArrayList<>(List.of(container)))
                .build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));
        when(dialectAdapterFactory.getAdapter(DatabaseType.POSTGRESQL)).thenReturn(dialectAdapter);
    }

    @Test
    void testStartSeedJob_SandboxNotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(sandboxRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.startSeedJob(unknownId, new DataSeedRequest()));
    }

    @Test
    void testStartSeedJob_SandboxDestroyed_ThrowsException() {
        sandbox.setStatus(SandboxStatus.DESTROYED);

        DataSeedingException ex = assertThrows(DataSeedingException.class,
                () -> service.startSeedJob(sandboxId, new DataSeedRequest()));
        assertTrue(ex.getMessage().contains("destroyed"));
    }

    @Test
    void testStartSeedJob_ContainerSchemaNotInitialized_ThrowsException() {
        container.setSchemaStatus(SchemaStatus.NOT_INITIALIZED);

        DataSeedingException ex = assertThrows(DataSeedingException.class,
                () -> service.startSeedJob(sandboxId, new DataSeedRequest()));
        assertTrue(ex.getMessage().contains("Schema is not initialized"));
    }

    @Test
    void testStartSeedJob_NoDataSourceLinked_ThrowsException() {
        container.setDataSource(null);

        DataSeedingException ex = assertThrows(DataSeedingException.class,
                () -> service.startSeedJob(sandboxId, new DataSeedRequest()));
        assertTrue(ex.getMessage().contains("No source DataSource is linked"));
    }

    @Test
    void testStartSeedJob_DuplicateActiveJob_ThrowsException() {
        when(seedJobRepository.existsBySandboxIdAndStatusIn(eq(sandboxId), any())).thenReturn(true);

        DataSeedingException ex = assertThrows(DataSeedingException.class,
                () -> service.startSeedJob(sandboxId, new DataSeedRequest()));
        assertTrue(ex.getMessage().contains("already active"));
    }

    @Test
    void testStartSeedJob_Success() throws Exception {
        when(seedJobRepository.existsBySandboxIdAndStatusIn(eq(sandboxId), any())).thenReturn(false);

        List<String> tables = List.of("users", "orders");
        when(schemaInspector.getSourceTables(any(), eq(DatabaseType.POSTGRESQL))).thenReturn(tables);
        when(dependencyPlanner.planDependencyOrder(any(), eq(tables))).thenReturn(tables);

        UUID jobId = UUID.randomUUID();
        when(seedJobRepository.save(any(SeedJob.class))).thenAnswer(inv -> {
            SeedJob j = inv.getArgument(0);
            if (j.getId() == null) {
                j.setId(jobId);
            }
            return j;
        });

        DataSeedRequest request = DataSeedRequest.builder()
                .mode(SeedingMode.FULL_DATABASE)
                .consistencyMode(ConsistencyMode.SNAPSHOT)
                .build();

        SeedJobResponse response = service.startSeedJob(sandboxId, request);

        assertNotNull(response);
        assertEquals(jobId, response.getId());
        assertEquals(SeedJobStatus.QUEUED, response.getStatus());
        assertEquals(2, response.getTotalTables());
        verify(seedJobWorker).submitJob(eq(jobId), eq(tables), eq(dialectAdapter));
    }

    @Test
    void testValidatePreflight_Success() throws Exception {
        List<String> tables = List.of("customers", "orders");
        when(schemaInspector.getSourceTables(any(), eq(DatabaseType.POSTGRESQL))).thenReturn(tables);
        when(dependencyPlanner.planDependencyOrder(any(), eq(tables))).thenReturn(tables);

        DataSeedValidationResponse validation = service.validatePreflight(sandboxId, new DataSeedRequest());

        assertTrue(validation.isValid());
        assertTrue(validation.getErrors().isEmpty());
        assertEquals(tables, validation.getDependencyOrder());
    }

    @Test
    void testCancelJob_Running_TransitionsToCancelling() {
        UUID jobId = UUID.randomUUID();
        SeedJob job = SeedJob.builder()
                .id(jobId)
                .sandbox(sandbox)
                .container(container)
                .sourceDataSource(dataSource)
                .status(SeedJobStatus.RUNNING)
                .build();

        when(seedJobRepository.findByIdAndSandboxId(jobId, sandboxId)).thenReturn(Optional.of(job));
        when(seedJobRepository.save(any(SeedJob.class))).thenAnswer(inv -> inv.getArgument(0));

        SeedJobResponse response = service.cancelJob(sandboxId, jobId);

        assertNotNull(response);
        assertEquals(SeedJobStatus.CANCELLING, response.getStatus());
    }
}
