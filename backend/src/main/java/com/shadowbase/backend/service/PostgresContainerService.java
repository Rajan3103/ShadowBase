package com.shadowbase.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.DataSource;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Exception.ContainerOperationException;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.DatasourceRepository;
import com.shadowbase.backend.dto.ContainerResponse;
import com.shadowbase.backend.dto.CreateContainerRequest;

import lombok.extern.slf4j.Slf4j;

/**
 * Service dedicated to managing the complete lifecycle of isolated PostgreSQL database containers.
 * <p>
 * Responsibilities include:
 * <ul>
 *   <li>Spinning up on-demand PostgreSQL containers via Testcontainers.</li>
 *   <li>Persisting and updating container metadata in the database without keeping in-memory state.</li>
 *   <li>Starting, stopping, and cleanly removing Docker containers via DockerClient.</li>
 *   <li>Reconciling database container records against the live Docker daemon on application startup.</li>
 * </ul>
 */
@Slf4j
@Service
public class PostgresContainerService {

    private final ContainerRepository containerRepository;
    private final DatasourceRepository datasourceRepository;

    /**
     * Single repository constructor for testing or basic setups.
     *
     * @param containerRepository repository to persist and query container records
     */
    public PostgresContainerService(ContainerRepository containerRepository) {
        this(containerRepository, null);
    }

    /**
     * Constructor injection for database and datasource repositories.
     *
     * @param containerRepository repository to persist and query container records
     * @param datasourceRepository repository to link source database configurations to the container
     */
    public PostgresContainerService(
            ContainerRepository containerRepository,
            DatasourceRepository datasourceRepository) {
        this.containerRepository = containerRepository;
        this.datasourceRepository = datasourceRepository;
    }

    /**
     * Convenience overload for creating a PostgreSQL container using individual connection parameters.
     *
     * @param sandbox      the parent sandbox entity
     * @param databaseName target database name
     * @param userName     database administrator username
     * @param password     database password
     * @return the newly created and running Container entity
     */
    public Container createContainer(Sandbox sandbox, String databaseName, String userName, String password) {
        CreateContainerRequest request = CreateContainerRequest.builder()
                .sandboxId(sandbox != null ? sandbox.getId() : null)
                .databaseType(DatabaseType.POSTGRESQL)
                .databaseName(databaseName)
                .username(userName)
                .password(password)
                .build();
        return createContainer(sandbox, request);
    }

    /**
     * Provisions and starts a new PostgreSQL Docker container based on the provided request parameters.
     * Extracts dynamic port mappings, JDBC connection URL, and host details, persisting them into the database.
     *
     * @param sandbox the sandbox environment owning this container
     * @param request configuration details for the container (database name, credentials, image)
     * @return the persisted Container entity in RUNNING status
     * @throws ContainerOperationException if Docker startup fails
     */
    @Transactional
    public Container createContainer(Sandbox sandbox, CreateContainerRequest request) {
        DatabaseType databaseType = DatabaseType.POSTGRESQL;

        // Apply fallback defaults if parameters are omitted
        String dbName = (request.getDatabaseName() != null && !request.getDatabaseName().isBlank())
                ? request.getDatabaseName()
                : "shadowbase_db";

        String username = (request.getUsername() != null && !request.getUsername().isBlank())
                ? request.getUsername()
                : "shadowuser";

        String password = (request.getPassword() != null && !request.getPassword().isBlank())
                ? request.getPassword()
                : "shadowpass";

        String imageName = (request.getImageName() != null && !request.getImageName().isBlank())
                ? request.getImageName()
                : "postgres:16-alpine";

        // Initialize Testcontainers PostgreSQL instance
        PostgreSQLContainer containerInstance = new PostgreSQLContainer(DockerImageName.parse(imageName))
                .withDatabaseName(dbName)
                .withUsername(username)
                .withPassword(password);

        log.info("Starting PostgreSQL container (image: {}) for sandbox {}...",
                containerInstance.getDockerImageName(), sandbox != null ? sandbox.getId() : "none");

        try {
            containerInstance.start();
        } catch (Exception e) {
            log.error("Failed to start PostgreSQL container for sandbox {}: {}",
                    sandbox != null ? sandbox.getId() : "none", e.getMessage(), e);
            try {
                containerInstance.stop();
            } catch (Exception ignored) {
            }

            // Persist a failure record so errors are traceable and auditable
            String fallbackId = "failed-" + UUID.randomUUID();
            Container failedContainer = Container.builder()
                    .id(fallbackId)
                    .sandbox(sandbox)
                    .databaseType(databaseType)
                    .databaseName(dbName)
                    .username(username)
                    .password(password)
                    .imageName(containerInstance.getDockerImageName())
                    .credentialReference(request.getCredentialReference())
                    .status(ContainerStatus.FAILED)
                    .build();
            containerRepository.save(failedContainer);

            throw new ContainerOperationException("PostgreSQL container startup failed: " + e.getMessage(), e);
        }

        // Extract container metadata from live Docker instance
        String containerId = containerInstance.getContainerId();
        String host = containerInstance.getHost();
        Integer port = containerInstance.getFirstMappedPort();
        String jdbcUrl = containerInstance.getJdbcUrl();
        String actualImage = containerInstance.getDockerImageName();

        log.info("PostgreSQL container started successfully. ID: {}, Port: {}, JDBC URL: {}",
                containerId, port, jdbcUrl);

        DataSource dataSource = null;
        if (request.getDataSourceId() != null && datasourceRepository != null) {
            dataSource = datasourceRepository.findById(request.getDataSourceId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "DataSource not found with ID: " + request.getDataSourceId()));

            // Enforce that a DataSource is linked to only one container
            if (containerRepository.existsByDataSourceId(request.getDataSourceId())) {
                throw new ContainerOperationException("DataSource " + request.getDataSourceId()
                        + " is already linked to an existing container. A DataSource can be linked to only one container.");
            }
        }

        Container container = Container.builder()
                .id(containerId)
                .sandbox(sandbox)
                .databaseType(databaseType)
                .databaseName(dbName)
                .username(username)
                .password(password)
                .host(host)
                .port(port)
                .jdbcUrl(jdbcUrl)
                .imageName(actualImage)
                .credentialReference(request.getCredentialReference())
                .dataSource(dataSource)
                .status(ContainerStatus.RUNNING)
                .build();

        return containerRepository.save(container);
    }

    /**
     * Starts an existing, stopped PostgreSQL container using the Docker daemon directly.
     *
     * @param containerId the Docker container ID
     * @return updated Container entity in RUNNING status
     */
    @Transactional
    public Container startContainer(String containerId) {
        Container container = getContainer(containerId);

        if (container.getStatus() == ContainerStatus.RUNNING) {
            log.info("PostgreSQL container {} is already RUNNING", containerId);
            return container;
        }

        container.setStatus(ContainerStatus.STARTING);
        containerRepository.save(container);

        try {
            if (isDockerAvailable()) {
                DockerClient client = getDockerClient();
                client.startContainerCmd(containerId).exec();
                updateMetadataFromDocker(container, client);
            } else {
                throw new ContainerOperationException("Docker daemon is not available to start container " + containerId);
            }

            container.setStatus(ContainerStatus.RUNNING);
            container.setStoppedAt(null);
            log.info("PostgreSQL container {} started successfully", containerId);
            return containerRepository.save(container);
        } catch (Exception e) {
            log.error("Failed to start PostgreSQL container {}: {}", containerId, e.getMessage(), e);
            container.setStatus(ContainerStatus.FAILED);
            containerRepository.save(container);
            throw new ContainerOperationException("Failed to start PostgreSQL container: " + e.getMessage(), e);
        }
    }

    /**
     * Stops a running PostgreSQL container with a grace period timeout.
     *
     * @param containerId the Docker container ID
     * @return updated Container entity in STOPPED status
     */
    @Transactional
    public Container stopContainer(String containerId) {
        Container container = getContainer(containerId);

        if (container.getStatus() == ContainerStatus.STOPPED) {
            log.info("PostgreSQL container {} is already STOPPED", containerId);
            return container;
        }

        container.setStatus(ContainerStatus.STOPPING);
        containerRepository.save(container);

        try {
            if (isDockerAvailable()) {
                DockerClient client = getDockerClient();
                client.stopContainerCmd(containerId).withTimeout(10).exec();
            }

            container.setStatus(ContainerStatus.STOPPED);
            container.setStoppedAt(LocalDateTime.now());
            log.info("PostgreSQL container {} stopped successfully", containerId);
            return containerRepository.save(container);
        } catch (Exception e) {
            log.error("Failed to stop PostgreSQL container {}: {}", containerId, e.getMessage(), e);
            container.setStatus(ContainerStatus.UNHEALTHY);
            containerRepository.save(container);
            throw new ContainerOperationException("Failed to stop PostgreSQL container: " + e.getMessage(), e);
        }
    }

    /**
     * Forcefully removes a PostgreSQL container from Docker and marks it as REMOVED in the database.
     *
     * @param containerId the Docker container ID
     */
    @Transactional
    public void removeContainer(String containerId) {
        Container container = getContainer(containerId);

        log.info("Removing PostgreSQL container {}...", containerId);
        try {
            if (isDockerAvailable()) {
                DockerClient client = getDockerClient();
                try {
                    client.removeContainerCmd(containerId).withForce(true).exec();
                } catch (NotFoundException e) {
                    log.debug("PostgreSQL container {} already removed in Docker", containerId);
                }
            }
        } catch (Exception e) {
            log.warn("Non-fatal error removing Docker container {}: {}", containerId, e.getMessage());
        }

        container.setStatus(ContainerStatus.REMOVED);
        container.setStoppedAt(LocalDateTime.now());
        containerRepository.save(container);
        log.info("PostgreSQL container {} marked as REMOVED", containerId);
    }

    /**
     * Retrieves a PostgreSQL container by its ID, ensuring it is a PostgreSQL instance.
     *
     * @param containerId the container identifier
     * @return the matching Container entity
     * @throws ResourceNotFoundException   if the container does not exist
     * @throws ContainerOperationException if the container is not PostgreSQL
     */
    @Transactional(readOnly = true)
    public Container getContainer(String containerId) {
        Container container = containerRepository.findById(containerId)
                .orElseThrow(() -> new ResourceNotFoundException("Container not found with ID: " + containerId));

        if (container.getDatabaseType() != DatabaseType.POSTGRESQL) {
            throw new ContainerOperationException("Container " + containerId + " is not a PostgreSQL container.");
        }

        return container;
    }

    /**
     * Retrieves container metadata mapped into a secure response DTO (omitting plain text passwords).
     *
     * @param containerId the container ID
     * @return safe ContainerResponse DTO
     */
    @Transactional(readOnly = true)
    public ContainerResponse getContainerDetails(String containerId) {
        return mapToResponse(getContainer(containerId));
    }

    /**
     * Lists all PostgreSQL containers associated with the specified sandbox.
     *
     * @param sandboxId the sandbox UUID
     * @return list of matching PostgreSQL Container entities
     */
    @Transactional(readOnly = true)
    public List<Container> getContainersBySandbox(UUID sandboxId) {
        return containerRepository.findBySandboxId(sandboxId).stream()
                .filter(c -> c.getDatabaseType() == DatabaseType.POSTGRESQL)
                .collect(Collectors.toList());
    }

    /**
     * Lists all PostgreSQL container responses associated with the specified sandbox.
     *
     * @param sandboxId the sandbox UUID
     * @return list of secure ContainerResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<ContainerResponse> getContainerResponsesBySandbox(UUID sandboxId) {
        return getContainersBySandbox(sandboxId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Startup listener that queries live Docker status for all candidate PostgreSQL containers
     * and reconciles their persisted state in the database.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileContainers() {
        log.info("Reconciling PostgreSQL containers against Docker daemon...");
        if (!isDockerAvailable()) {
            log.warn("Docker is not reachable. Skipping startup reconciliation.");
            return;
        }

        DockerClient client = getDockerClient();
        List<Container> candidateContainers =
                containerRepository.findByDatabaseTypeAndStatusIn(
                        DatabaseType.POSTGRESQL,
                        List.of(
                                ContainerStatus.RUNNING,
                                ContainerStatus.STARTING,
                                ContainerStatus.STOPPING
                        )
                );

        for (Container c : candidateContainers) {
            try {
                InspectContainerResponse inspect = client.inspectContainerCmd(c.getId()).exec();
                if (inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning())) {
                    c.setStatus(ContainerStatus.RUNNING);
                    updateMetadataFromDocker(c, client);
                } else {
                    c.setStatus(ContainerStatus.STOPPED);
                    if (c.getStoppedAt() == null) {
                        c.setStoppedAt(LocalDateTime.now());
                    }
                }
            } catch (NotFoundException e) {
                log.warn("PostgreSQL container {} not found in Docker. Marking REMOVED.", c.getId());
                c.setStatus(ContainerStatus.REMOVED);
                if (c.getStoppedAt() == null) {
                    c.setStoppedAt(LocalDateTime.now());
                }
            } catch (Exception e) {
                log.error("Error inspecting container {}: {}", c.getId(), e.getMessage());
                c.setStatus(ContainerStatus.UNHEALTHY);
            }
            containerRepository.save(c);
        }

        log.info("PostgreSQL container reconciliation completed for {} records.", candidateContainers.size());
    }

    /**
     * Maps a Container entity to a ContainerResponse DTO, carefully omitting sensitive credentials.
     *
     * @param container the raw entity
     * @return safe DTO for API responses
     */
    public ContainerResponse mapToResponse(Container container) {
        if (container == null) {
            return null;
        }
        return ContainerResponse.builder()
                .id(container.getId())
                .sandboxId(container.getSandbox() != null ? container.getSandbox().getId() : null)
                .imageName(container.getImageName())
                .host(container.getHost())
                .port(container.getPort())
                .jdbcUrl(container.getJdbcUrl())
                .username(container.getUsername())
                .databaseName(container.getDatabaseName())
                .databaseType(container.getDatabaseType())
                .status(container.getStatus())
                .schemaStatus(container.getSchemaStatus())
                .credentialReference(container.getCredentialReference())
                .dataSourceId(container.getDataSource() != null ? container.getDataSource().getId() : null)
                .createdAt(container.getCreatedAt())
                .updatedAt(container.getUpdatedAt())
                .expiresAt(container.getExpiresAt())
                .stoppedAt(container.getStoppedAt())
                .build();
    }

    private boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    private DockerClient getDockerClient() {
        return DockerClientFactory.instance().client();
    }

    private void updateMetadataFromDocker(Container container, DockerClient client) {
        try {
            InspectContainerResponse inspect = client.inspectContainerCmd(container.getId()).exec();
            if (inspect.getNetworkSettings() != null) {
                Ports ports = inspect.getNetworkSettings().getPorts();
                if (ports != null && ports.getBindings() != null) {
                    for (Map.Entry<ExposedPort, Ports.Binding[]> entry : ports.getBindings().entrySet()) {
                        if (entry.getValue() != null && entry.getValue().length > 0) {
                            String hostPortStr = entry.getValue()[0].getHostPortSpec();
                            if (hostPortStr != null) {
                                int mappedPort = Integer.parseInt(hostPortStr);
                                container.setPort(mappedPort);
                                container.setHost(DockerClientFactory.instance().dockerHostIpAddress());
                                container.setJdbcUrl("jdbc:postgresql://" + container.getHost() + ":" + mappedPort + "/" + container.getDatabaseName());
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not refresh Docker network settings for PostgreSQL container {}: {}", container.getId(), e.getMessage());
        }
    }
}
