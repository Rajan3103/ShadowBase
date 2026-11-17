package com.shadowbase.backend.service.seeding;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.DataSource;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.SchemaStatus;
import com.shadowbase.backend.Entity.SeedJob;
import com.shadowbase.backend.Entity.SeedJobStatus;
import com.shadowbase.backend.Entity.SeedTableProgress;
import com.shadowbase.backend.Entity.SeedTableStatus;
import com.shadowbase.backend.Entity.SeedingMode;
import com.shadowbase.backend.Exception.DataSeedingException;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.Repository.SeedJobRepository;
import com.shadowbase.backend.Repository.SeedTableProgressRepository;
import com.shadowbase.backend.dto.DataSeedRequest;
import com.shadowbase.backend.dto.DataSeedValidationResponse;
import com.shadowbase.backend.dto.SeedJobResponse;
import com.shadowbase.backend.dto.SeedTableProgressResponse;
import com.shadowbase.backend.service.ContainerConnectionProvider;
import com.shadowbase.backend.service.adapter.DatabaseDialectAdapter;
import com.shadowbase.backend.service.adapter.DatabaseDialectAdapterFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link DataSeedingService} managing end-to-end data seeding workflows,
 * preflight validation, duplicate execution prevention, and job lifecycle queries.
 */
@Slf4j
@Service
public class DataSeedingServiceImpl implements DataSeedingService {

    private static final Set<SeedJobStatus> ACTIVE_STATUSES = Set.of(
            SeedJobStatus.QUEUED,
            SeedJobStatus.VALIDATING,
            SeedJobStatus.RUNNING,
            SeedJobStatus.CANCELLING
    );

    private final SandboxRepository sandboxRepository;
    private final ContainerRepository containerRepository;
    private final SeedJobRepository seedJobRepository;
    private final SeedTableProgressRepository tableProgressRepository;
    private final SourceConnectionProvider sourceConnectionProvider;
    private final ContainerConnectionProvider containerConnectionProvider;
    private final SchemaInspector schemaInspector;
    private final TableDependencyPlanner dependencyPlanner;
    private final DatabaseDialectAdapterFactory dialectAdapterFactory;
    private final SeedJobWorker seedJobWorker;

    public DataSeedingServiceImpl(SandboxRepository sandboxRepository,
                                  ContainerRepository containerRepository,
                                  SeedJobRepository seedJobRepository,
                                  SeedTableProgressRepository tableProgressRepository,
                                  SourceConnectionProvider sourceConnectionProvider,
                                  ContainerConnectionProvider containerConnectionProvider,
                                  SchemaInspector schemaInspector,
                                  TableDependencyPlanner dependencyPlanner,
                                  DatabaseDialectAdapterFactory dialectAdapterFactory,
                                  SeedJobWorker seedJobWorker) {
        this.sandboxRepository = sandboxRepository;
        this.containerRepository = containerRepository;
        this.seedJobRepository = seedJobRepository;
        this.tableProgressRepository = tableProgressRepository;
        this.sourceConnectionProvider = sourceConnectionProvider;
        this.containerConnectionProvider = containerConnectionProvider;
        this.schemaInspector = schemaInspector;
        this.dependencyPlanner = dependencyPlanner;
        this.dialectAdapterFactory = dialectAdapterFactory;
        this.seedJobWorker = seedJobWorker;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public SeedJobResponse startSeedJob(UUID sandboxId, DataSeedRequest request) {
        log.info("Request to start data seeding for sandbox: {}", sandboxId);

        if (request == null) {
            request = new DataSeedRequest();
        }

        Sandbox sandbox = getAndValidateSandbox(sandboxId, request.getUserId());
        Container container = resolveTargetContainer(sandbox, request.getContainerId());
        validateContainerAndSchema(container);

        DataSource dataSource = container.getDataSource();
        if (dataSource == null) {
            throw new DataSeedingException("No source DataSource is linked to container "
                    + container.getId() + ". Data seeding requires a linked source database.");
        }

        // Prevent concurrent active seed jobs for the same sandbox
        if (seedJobRepository.existsBySandboxIdAndStatusIn(sandboxId, ACTIVE_STATUSES)) {
            throw new DataSeedingException("A data seeding job is already active for sandbox " + sandboxId);
        }

        DatabaseDialectAdapter adapter = dialectAdapterFactory.getAdapter(container.getDatabaseType());

        // Preflight validation to determine ordered table list
        List<String> orderedTables = performPreflightValidation(container, dataSource, adapter, request);

        if (orderedTables.isEmpty()) {
            throw new DataSeedingException("No tables found or requested for data seeding.");
        }

        // Create persistent SeedJob in QUEUED status
        SeedJob job = SeedJob.builder()
                .sandbox(sandbox)
                .container(container)
                .sourceDataSource(dataSource)
                .status(SeedJobStatus.QUEUED)
                .mode(request.getMode())
                .consistencyMode(request.getConsistencyMode())
                .totalTables(orderedTables.size())
                .completedTables(0)
                .totalRowsRead(0L)
                .totalRowsInserted(0L)
                .createdBy(request.getUserId() != null ? request.getUserId().toString() : "anonymous")
                .tableProgressList(new ArrayList<>())
                .build();

        for (String tableName : orderedTables) {
            SeedTableProgress progress = SeedTableProgress.builder()
                    .seedJob(job)
                    .tableName(tableName)
                    .status(SeedTableStatus.PENDING)
                    .rowsRead(0L)
                    .rowsInserted(0L)
                    .build();
            job.getTableProgressList().add(progress);
        }

        job = seedJobRepository.save(job);
        log.info("Persisted SeedJob {} with {} planned tables", job.getId(), orderedTables.size());

        // Delegate to asynchronous background worker
        seedJobWorker.submitJob(job.getId(), orderedTables, adapter);

        return mapToResponse(job);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public DataSeedValidationResponse validatePreflight(UUID sandboxId, DataSeedRequest request) {
        log.info("Executing preflight data seed validation for sandbox: {}", sandboxId);

        if (request == null) {
            request = new DataSeedRequest();
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> plannedOrder = new ArrayList<>();

        try {
            Sandbox sandbox = getAndValidateSandbox(sandboxId, request.getUserId());
            Container container = resolveTargetContainer(sandbox, request.getContainerId());
            validateContainerAndSchema(container);

            DataSource dataSource = container.getDataSource();
            if (dataSource == null) {
                errors.add("No source DataSource linked to container " + container.getId());
                return new DataSeedValidationResponse(false, errors, warnings, Collections.emptyList(), Collections.emptyList());
            }

            DatabaseDialectAdapter adapter = dialectAdapterFactory.getAdapter(container.getDatabaseType());
            plannedOrder = performPreflightValidation(container, dataSource, adapter, request);

        } catch (Exception e) {
            errors.add(e.getMessage());
        }

        boolean valid = errors.isEmpty();
        return DataSeedValidationResponse.builder()
                .valid(valid)
                .errors(errors)
                .warnings(warnings)
                .tablesToTransfer(plannedOrder)
                .dependencyOrder(plannedOrder)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public SeedJobResponse getJob(UUID sandboxId, UUID jobId) {
        SeedJob job = seedJobRepository.findByIdAndSandboxId(jobId, sandboxId)
                .orElseThrow(() -> new ResourceNotFoundException("SeedJob not found with ID: " + jobId + " for sandbox: " + sandboxId));
        return mapToResponse(job);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<SeedJobResponse> listJobs(UUID sandboxId) {
        // Validate sandbox exists
        sandboxRepository.findById(sandboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Sandbox not found with ID: " + sandboxId));

        return seedJobRepository.findBySandboxIdOrderByCreatedAtDesc(sandboxId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public SeedJobResponse cancelJob(UUID sandboxId, UUID jobId) {
        SeedJob job = seedJobRepository.findByIdAndSandboxId(jobId, sandboxId)
                .orElseThrow(() -> new ResourceNotFoundException("SeedJob not found with ID: " + jobId));

        if (job.getStatus() == SeedJobStatus.RUNNING || job.getStatus() == SeedJobStatus.QUEUED
                || job.getStatus() == SeedJobStatus.VALIDATING) {
            job.setStatus(SeedJobStatus.CANCELLING);
            job = seedJobRepository.save(job);
            log.info("Requested cancellation for SeedJob {}", jobId);
        } else {
            log.info("SeedJob {} is already in terminal status: {}", jobId, job.getStatus());
        }

        return mapToResponse(job);
    }

    private List<String> performPreflightValidation(Container container,
                                                    DataSource dataSource,
                                                    DatabaseDialectAdapter adapter,
                                                    DataSeedRequest request) {

        try (Connection sourceConn = sourceConnectionProvider.openSourceConnection(dataSource, container.getDatabaseType());
             Connection targetConn = containerConnectionProvider.openConnection(container.getId())) {

            List<String> rawTables;
            if (request.getMode() == SeedingMode.FULL_DATABASE || request.getTables() == null || request.getTables().isEmpty()) {
                rawTables = schemaInspector.getSourceTables(sourceConn, container.getDatabaseType());
            } else {
                rawTables = request.getTables();
            }

            if (rawTables.isEmpty()) {
                throw new DataSeedingException("No tables found or requested in the database");
            }

            // Verify tables exist in both source and target
            schemaInspector.validateTablesExist(sourceConn, targetConn, rawTables, container.getDatabaseType());

            // Validate column compatibility for every table
            for (String table : rawTables) {
                schemaInspector.validateColumnCompatibility(sourceConn, targetConn, table, adapter);
            }

            // Verify all target tables are empty
            schemaInspector.verifyTargetTablesEmpty(targetConn, rawTables, adapter);

            // Plan topological dependency order
            return dependencyPlanner.planDependencyOrder(targetConn, rawTables);

        } catch (DataSeedingException e) {
            throw e;
        } catch (Exception e) {
            throw new DataSeedingException("Preflight validation error: " + e.getMessage(), e);
        }
    }

    private Sandbox getAndValidateSandbox(UUID sandboxId, UUID userId) {
        Sandbox sandbox = sandboxRepository.findById(sandboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Sandbox not found with ID: " + sandboxId));

        if (sandbox.getStatus() == SandboxStatus.DESTROYED) {
            throw new DataSeedingException("Sandbox " + sandboxId + " has been decommissioned and destroyed");
        }

        if (userId != null && sandbox.getUser() != null && !userId.equals(sandbox.getUser().getId())) {
            throw new DataSeedingException("User " + userId + " is not authorized to seed data for sandbox " + sandboxId);
        }

        return sandbox;
    }

    private Container resolveTargetContainer(Sandbox sandbox, String requestedContainerId) {
        if (requestedContainerId != null && !requestedContainerId.isBlank()) {
            return containerRepository.findByIdAndSandboxId(requestedContainerId, sandbox.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Container " + requestedContainerId + " not found in sandbox " + sandbox.getId()));
        }

        List<Container> runningContainers = sandbox.getContainers().stream()
                .filter(c -> c.getStatus() == ContainerStatus.RUNNING)
                .toList();

        if (runningContainers.isEmpty()) {
            throw new DataSeedingException("Sandbox " + sandbox.getId() + " has no active running database containers");
        }

        if (runningContainers.size() > 1) {
            throw new DataSeedingException("Sandbox " + sandbox.getId()
                    + " contains multiple running containers. Please specify 'containerId' explicitly.");
        }

        return runningContainers.get(0);
    }

    private void validateContainerAndSchema(Container container) {
        if (container.getStatus() != ContainerStatus.RUNNING) {
            throw new DataSeedingException("Container " + container.getId() + " is not RUNNING (status: " + container.getStatus() + ")");
        }

        if (container.getSchemaStatus() != SchemaStatus.SUCCESS) {
            throw new DataSeedingException("Schema is not initialized for container " + container.getId()
                    + " (schemaStatus: " + container.getSchemaStatus() + "). Successful schema import is required before seeding.");
        }
    }

    private SeedJobResponse mapToResponse(SeedJob job) {
        List<SeedTableProgressResponse> tableResponses = new ArrayList<>();
        if (job.getTableProgressList() != null) {
            tableResponses = job.getTableProgressList().stream()
                    .map(p -> SeedTableProgressResponse.builder()
                            .id(p.getId())
                            .tableName(p.getTableName())
                            .status(p.getStatus())
                            .rowsRead(p.getRowsRead())
                            .rowsInserted(p.getRowsInserted())
                            .startedAt(p.getStartedAt())
                            .completedAt(p.getCompletedAt())
                            .errorMessage(p.getErrorMessage())
                            .build())
                    .toList();
        }

        return SeedJobResponse.builder()
                .id(job.getId())
                .sandboxId(job.getSandbox().getId())
                .containerId(job.getContainer().getId())
                .sourceDataSourceId(job.getSourceDataSource().getId())
                .status(job.getStatus())
                .mode(job.getMode())
                .consistencyMode(job.getConsistencyMode())
                .totalTables(job.getTotalTables())
                .completedTables(job.getCompletedTables())
                .totalRowsRead(job.getTotalRowsRead())
                .totalRowsInserted(job.getTotalRowsInserted())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .createdBy(job.getCreatedBy())
                .tables(tableResponses)
                .build();
    }
}
