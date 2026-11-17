package com.shadowbase.backend.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.DataSource;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.DatasourceRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.dto.CreateDataSourceRequest;
import com.shadowbase.backend.dto.DataSourceResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link DataSourceService}.
 */
@Slf4j
@Service
public class DataSourceServiceImpl implements DataSourceService {

    private final DatasourceRepository datasourceRepository;
    private final ContainerRepository containerRepository;
    private final SandboxRepository sandboxRepository;

    public DataSourceServiceImpl(
            DatasourceRepository datasourceRepository,
            ContainerRepository containerRepository,
            SandboxRepository sandboxRepository) {
        this.datasourceRepository = datasourceRepository;
        this.containerRepository = containerRepository;
        this.sandboxRepository = sandboxRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<DataSourceResponse> listDataSources() {
        return datasourceRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public DataSourceResponse getDataSource(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("DataSource ID must not be null");
        }
        DataSource ds = datasourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataSource not found with ID: " + id));
        return toResponse(ds);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public DataSourceResponse createDataSource(CreateDataSourceRequest request) {
        validateRequest(request);

        DataSource ds = DataSource.builder()
                .connectionName(request.getConnectionName().trim())
                .host(request.getHost().trim())
                .port(request.getPort())
                .databaseName(request.getDatabaseName().trim())
                .databaseUsername(request.getDatabaseUsername().trim())
                .databasePassword(request.getDatabasePassword())
                .build();

        DataSource saved = datasourceRepository.save(ds);
        log.info("Registered new DataSource '{}' with ID: {}", saved.getConnectionName(), saved.getId());
        return toResponse(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public DataSourceResponse linkToContainer(UUID sandboxId, String containerId, UUID dataSourceId) {
        if (sandboxId == null) {
            throw new IllegalArgumentException("Sandbox ID must not be null");
        }
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalArgumentException("Container ID must not be blank");
        }
        if (dataSourceId == null) {
            throw new IllegalArgumentException("DataSource ID must not be null");
        }

        Sandbox sandbox = sandboxRepository.findById(sandboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Sandbox not found with ID: " + sandboxId));

        Container container = containerRepository.findById(containerId)
                .filter(c -> c.getSandbox() != null && c.getSandbox().getId().equals(sandbox.getId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Container " + containerId + " not found in sandbox " + sandboxId));

        DataSource dataSource = datasourceRepository.findById(dataSourceId)
                .orElseThrow(() -> new ResourceNotFoundException("DataSource not found with ID: " + dataSourceId));

        container.setDataSource(dataSource);
        containerRepository.save(container);

        log.info("Linked DataSource '{}' ({}) to container {}",
                dataSource.getConnectionName(), dataSource.getId(), container.getId());

        return toResponse(dataSource, container.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public DataSourceResponse createAndLinkToContainer(UUID sandboxId, String containerId, CreateDataSourceRequest request) {
        DataSourceResponse created = createDataSource(request);
        return linkToContainer(sandboxId, containerId, created.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteDataSource(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("DataSource ID must not be null");
        }
        DataSource ds = datasourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DataSource not found with ID: " + id));

        // Unlink from container if bound
        containerRepository.findByDataSourceId(id).ifPresent(container -> {
            container.setDataSource(null);
            containerRepository.save(container);
            log.info("Unlinked DataSource {} from container {}", id, container.getId());
        });

        datasourceRepository.delete(ds);
        log.info("Deleted DataSource ID: {}", id);
    }

    private void validateRequest(CreateDataSourceRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CreateDataSourceRequest must not be null");
        }
        if (request.getConnectionName() == null || request.getConnectionName().trim().isEmpty()) {
            throw new IllegalArgumentException("Connection name must not be blank");
        }
        if (request.getHost() == null || request.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("Host must not be blank");
        }
        if (request.getPort() == null || request.getPort() <= 0 || request.getPort() > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (request.getDatabaseName() == null || request.getDatabaseName().trim().isEmpty()) {
            throw new IllegalArgumentException("Database name must not be blank");
        }
        if (request.getDatabaseUsername() == null || request.getDatabaseUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Database username must not be blank");
        }
    }

    private DataSourceResponse toResponse(DataSource ds) {
        String containerId = ds.getContainer() != null ? ds.getContainer().getId() : null;
        return toResponse(ds, containerId);
    }

    private DataSourceResponse toResponse(DataSource ds, String containerId) {
        return DataSourceResponse.builder()
                .id(ds.getId())
                .connectionName(ds.getConnectionName())
                .host(ds.getHost())
                .port(ds.getPort())
                .databaseName(ds.getDatabaseName())
                .databaseUsername(ds.getDatabaseUsername())
                .containerId(containerId)
                .createdAt(ds.getCreatedAt())
                .updatedAt(ds.getUpdatedAt())
                .build();
    }
}
