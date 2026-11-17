package com.shadowbase.backend.service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.SchemaImport;
import com.shadowbase.backend.Entity.SchemaStatus;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Exception.SchemaImportException;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.Repository.SchemaImportRepository;
import com.shadowbase.backend.dto.SchemaImportResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link SchemaService} coordinating the complete schema import workflow.
 * <p>
 * Core Architectural Guarantees:
 * <ul>
 *   <li>A single container contains only one database instance.</li>
 *   <li>A container has only one schema belonging to its running database.</li>
 *   <li>Schema status is tracked per-container and aggregated across the parent sandbox.</li>
 *   <li>Re-import into an already initialized container is rejected to preserve data integrity.</li>
 * </ul>
 */
@Slf4j
@Service
public class SchemaServiceImpl implements SchemaService {

    private final SandboxRepository sandboxRepository;
    private final ContainerRepository containerRepository;
    private final SchemaImportRepository schemaImportRepository;
    private final ContainerConnectionProvider containerConnectionProvider;
    private final SqlScriptExecutor sqlScriptExecutor;
    private final long maxFileSizeBytes;

    public SchemaServiceImpl(
            SandboxRepository sandboxRepository,
            ContainerRepository containerRepository,
            SchemaImportRepository schemaImportRepository,
            ContainerConnectionProvider containerConnectionProvider,
            SqlScriptExecutor sqlScriptExecutor,
            @Value("${shadowbase.schema.max-file-size:10485760}") long maxFileSizeBytes) {

        this.sandboxRepository = sandboxRepository;
        this.containerRepository = containerRepository;
        this.schemaImportRepository = schemaImportRepository;
        this.containerConnectionProvider = containerConnectionProvider;
        this.sqlScriptExecutor = sqlScriptExecutor;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public SchemaImportResponse importSchema(UUID sandboxId, String containerId, MultipartFile file, UUID requestingUserId) {

        log.info("Processing schema import request for sandbox: {}, containerId: {}", sandboxId, containerId);

        // 1. Validate the uploaded file
        validateUploadedFile(file);

        // 2. Compute SHA-256 checksum for audit and integrity
        String checksum = computeSha256Checksum(file);

        // 3. Retrieve and validate the target sandbox
        Sandbox sandbox = sandboxRepository.findById(sandboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Sandbox not found with ID: " + sandboxId));

        if (sandbox.getStatus() == SandboxStatus.DESTROYED) {
            throw new SchemaImportException("Cannot import schema into destroyed sandbox: " + sandboxId);
        }

        // Authorization check
        if (requestingUserId != null && sandbox.getUser() != null) {
            if (!sandbox.getUser().getId().equals(requestingUserId)) {
                throw new SchemaImportException(
                        "User " + requestingUserId + " is not authorized to import schema into sandbox: " + sandboxId);
            }
        }

        // 4. Resolve the specific target container (single database, single schema)
        Container targetContainer = resolveTargetContainer(sandbox, containerId);

        // 5. Guard against concurrent or duplicate imports on this specific container
        if (schemaImportRepository.existsByContainerIdAndStatus(targetContainer.getId(), SchemaStatus.IN_PROGRESS)) {
            throw new SchemaImportException(
                    "A schema import is already in progress for container: " + targetContainer.getId());
        }

        if (targetContainer.getSchemaStatus() == SchemaStatus.SUCCESS) {
            throw new SchemaImportException(
                    "Schema has already been successfully initialized for container: " + targetContainer.getId()
                    + " (" + targetContainer.getDatabaseName() + "). A container hosts only one database and one schema.");
        }

        // 6. Verify database readiness
        if (!containerConnectionProvider.isDatabaseReady(targetContainer)) {
            throw new SchemaImportException(
                    "Database '" + targetContainer.getDatabaseName() + "' in container " + targetContainer.getId()
                    + " (" + targetContainer.getDatabaseType() + ") is not ready to accept connections.");
        }

        // 7. Persist SchemaImport record with IN_PROGRESS status
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "schema.sql";
        SchemaImport schemaImport = SchemaImport.builder()
                .sandbox(sandbox)
                .containerId(targetContainer.getId())
                .fileName(originalFilename)
                .databaseType(targetContainer.getDatabaseType())
                .checksum(checksum)
                .status(SchemaStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();

        schemaImport = schemaImportRepository.save(schemaImport);

        // Update container and sandbox schema statuses to IN_PROGRESS
        targetContainer.setSchemaStatus(SchemaStatus.IN_PROGRESS);
        containerRepository.save(targetContainer);
        updateSandboxAggregateSchemaStatus(sandbox);

        // 8. Execute SQL file via ContainerConnectionProvider & SqlScriptExecutor
        try (Connection connection = containerConnectionProvider.openConnection(targetContainer);
             InputStream inputStream = file.getInputStream()) {

            sqlScriptExecutor.execute(connection, inputStream, targetContainer.getDatabaseType());

            // 9. Mark SUCCESS on container, schema import record, and sandbox
            targetContainer.setSchemaStatus(SchemaStatus.SUCCESS);
            containerRepository.save(targetContainer);

            schemaImport.setStatus(SchemaStatus.SUCCESS);
            schemaImport.setCompletedAt(LocalDateTime.now());
            schemaImport = schemaImportRepository.save(schemaImport);

            updateSandboxAggregateSchemaStatus(sandbox);

            log.info("Schema import {} succeeded for container {} in sandbox {}",
                    schemaImport.getId(), targetContainer.getId(), sandboxId);
            return mapToResponse(schemaImport);

        } catch (Exception e) {
            // 10. Mark FAILED on container, schema import record, and sandbox
            log.error("Schema import failed for container {} in sandbox {}: {}",
                    targetContainer.getId(), sandboxId, e.getMessage());

            targetContainer.setSchemaStatus(SchemaStatus.FAILED);
            containerRepository.save(targetContainer);

            schemaImport.setStatus(SchemaStatus.FAILED);
            schemaImport.setCompletedAt(LocalDateTime.now());
            schemaImport.setErrorMessage(sanitizeErrorMessage(e.getMessage()));
            schemaImport = schemaImportRepository.save(schemaImport);

            updateSandboxAggregateSchemaStatus(sandbox);

            throw new SchemaImportException("Schema import execution failed: " + schemaImport.getErrorMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public SchemaImportResponse importSchema(UUID sandboxId, MultipartFile file, UUID requestingUserId) {
        return importSchema(sandboxId, null, file, requestingUserId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public SchemaImportResponse getLatestImportStatus(UUID sandboxId, String containerId) {
        if (!sandboxRepository.existsById(sandboxId)) {
            throw new ResourceNotFoundException("Sandbox not found with ID: " + sandboxId);
        }

        if (containerId != null && !containerId.isBlank()) {
            return schemaImportRepository.findFirstBySandboxIdAndContainerIdOrderByStartedAtDesc(sandboxId, containerId)
                    .map(this::mapToResponse)
                    .orElse(null);
        }

        return schemaImportRepository.findFirstBySandboxIdOrderByStartedAtDesc(sandboxId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public SchemaImportResponse getLatestImportStatus(UUID sandboxId) {
        return getLatestImportStatus(sandboxId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<SchemaImportResponse> getImportHistory(UUID sandboxId, String containerId) {
        if (!sandboxRepository.existsById(sandboxId)) {
            throw new ResourceNotFoundException("Sandbox not found with ID: " + sandboxId);
        }

        if (containerId != null && !containerId.isBlank()) {
            return schemaImportRepository.findBySandboxIdAndContainerIdOrderByStartedAtDesc(sandboxId, containerId)
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
        }

        return schemaImportRepository.findBySandboxIdOrderByStartedAtDesc(sandboxId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<SchemaImportResponse> getImportHistory(UUID sandboxId) {
        return getImportHistory(sandboxId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public SchemaImportResponse getContainerLatestImportStatus(String containerId) {
        if (!containerRepository.existsById(containerId)) {
            throw new ResourceNotFoundException("Container not found with ID: " + containerId);
        }

        return schemaImportRepository.findFirstByContainerIdOrderByStartedAtDesc(containerId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<SchemaImportResponse> getContainerImportHistory(String containerId) {
        if (!containerRepository.existsById(containerId)) {
            throw new ResourceNotFoundException("Container not found with ID: " + containerId);
        }

        return schemaImportRepository.findByContainerIdOrderByStartedAtDesc(containerId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void validateUploadedFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new SchemaImportException("Uploaded SQL file must not be null or empty.");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new SchemaImportException(
                    String.format("File size (%d bytes) exceeds maximum allowable limit of %d bytes.",
                            file.getSize(), maxFileSizeBytes));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".sql")) {
            throw new SchemaImportException("Invalid file format. Only plaintext '.sql' files are supported.");
        }
    }

    private String computeSha256Checksum(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new SchemaImportException("Failed to calculate file checksum: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the target container for schema import.
     * <p>
     * If containerId is explicitly provided, verifies it belongs to the sandbox.
     * Otherwise, locates an available RUNNING container in the sandbox that has not yet been initialized.
     */
    private Container resolveTargetContainer(Sandbox sandbox, String containerId) {
        if (containerId != null && !containerId.isBlank()) {
            Container container = containerRepository.findById(containerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Container not found with ID: " + containerId));

            if (container.getSandbox() == null || !container.getSandbox().getId().equals(sandbox.getId())) {
                throw new SchemaImportException(
                        "Container " + containerId + " does not belong to sandbox " + sandbox.getId());
            }

            if (container.getStatus() != ContainerStatus.RUNNING) {
                throw new SchemaImportException(
                        "Target container " + containerId + " is currently " + container.getStatus()
                        + ". Container must be RUNNING to import schema.");
            }

            return container;
        }

        List<Container> containers = sandbox.getContainers();
        if (containers == null || containers.isEmpty()) {
            throw new SchemaImportException(
                    "Sandbox " + sandbox.getId() + " has no associated database containers.");
        }

        List<Container> runningContainers = containers.stream()
                .filter(c -> c.getStatus() == ContainerStatus.RUNNING)
                .collect(Collectors.toList());

        if (runningContainers.isEmpty()) {
            throw new SchemaImportException(
                    "No RUNNING database container found for sandbox " + sandbox.getId()
                    + ". A running container is required to import schema.");
        }

        if (runningContainers.size() == 1) {
            return runningContainers.get(0);
        }

        // Multiple running containers: find the first container not yet successfully initialized
        return runningContainers.stream()
                .filter(c -> c.getSchemaStatus() != SchemaStatus.SUCCESS && c.getSchemaStatus() != SchemaStatus.IN_PROGRESS)
                .findFirst()
                .orElseThrow(() -> new SchemaImportException(
                        "All " + runningContainers.size() + " database containers in sandbox " + sandbox.getId()
                        + " already have their schemas initialized. Please specify 'containerId' to target a specific container."));
    }

    /**
     * Aggregates the schema statuses across all containers belonging to the sandbox.
     */
    private void updateSandboxAggregateSchemaStatus(Sandbox sandbox) {
        List<Container> containers = sandbox.getContainers();
        if (containers == null || containers.isEmpty()) {
            sandbox.setSchemaStatus(SchemaStatus.NOT_INITIALIZED);
            sandboxRepository.save(sandbox);
            return;
        }

        boolean anyInProgress = containers.stream()
                .anyMatch(c -> c.getSchemaStatus() == SchemaStatus.IN_PROGRESS);

        boolean allSuccess = containers.stream()
                .allMatch(c -> c.getSchemaStatus() == SchemaStatus.SUCCESS);

        boolean anyFailed = containers.stream()
                .anyMatch(c -> c.getSchemaStatus() == SchemaStatus.FAILED);

        if (anyInProgress) {
            sandbox.setSchemaStatus(SchemaStatus.IN_PROGRESS);
        } else if (allSuccess) {
            sandbox.setSchemaStatus(SchemaStatus.SUCCESS);
        } else if (anyFailed) {
            sandbox.setSchemaStatus(SchemaStatus.FAILED);
        } else {
            sandbox.setSchemaStatus(SchemaStatus.NOT_INITIALIZED);
        }

        sandboxRepository.save(sandbox);
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown database execution error.";
        }
        String clean = message.trim();
        return clean.length() > 3900 ? clean.substring(0, 3900) + "..." : clean;
    }

    private SchemaImportResponse mapToResponse(SchemaImport importRecord) {
        return SchemaImportResponse.builder()
                .id(importRecord.getId())
                .sandboxId(importRecord.getSandbox() != null ? importRecord.getSandbox().getId() : null)
                .containerId(importRecord.getContainerId())
                .fileName(importRecord.getFileName())
                .databaseType(importRecord.getDatabaseType())
                .checksum(importRecord.getChecksum())
                .status(importRecord.getStatus())
                .startedAt(importRecord.getStartedAt())
                .completedAt(importRecord.getCompletedAt())
                .errorMessage(importRecord.getErrorMessage())
                .build();
    }
}
