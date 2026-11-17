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
import org.testcontainers.mysql.MySQLContainer;
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
 * Service dedicated to managing the complete lifecycle of isolated MySQL database containers.
 * <p>
 * Responsibilities include:
 * <ul>
 *   <li>Spinning up on-demand MySQL containers via Testcontainers.</li>
 *   <li>Persisting and updating container metadata in the database without relying on in-memory state.</li>
 *   <li>Starting, stopping, and removing Docker containers via DockerClient.</li>
 *   <li>Reconciling database container records against the live Docker daemon on application startup.</li>
 * </ul>
 */
@Slf4j
@Service
public class MySQLContainerService {

    private final ContainerRepository containerRepository;
    private final DatasourceRepository datasourceRepository;

    /**
     * Single repository constructor for testing or basic setups.
     *
     * @param containerRepository repository to persist and query container records
     */
    public MySQLContainerService(ContainerRepository containerRepository) {
        this(containerRepository, null);
    }

    /**
     * Constructor injection for database and datasource repositories.
     *
     * @param containerRepository repository to persist and query container records
     * @param datasourceRepository repository to link source database configurations to the container
     */
    public MySQLContainerService(
            ContainerRepository containerRepository,
            DatasourceRepository datasourceRepository) {
        this.containerRepository = containerRepository;
        this.datasourceRepository = datasourceRepository;
    }

    /**
     * Provisions and starts a new MySQL Docker container based on the provided request parameters.
     * Extracts dynamic port mappings, JDBC connection URL, and host details, persisting them into the database.
     *
     * @param sandbox the sandbox environment owning this container
     * @param request configuration details for the container (database name, credentials, image)
     * @return the persisted Container entity in RUNNING status
     * @throws ContainerOperationException if Docker startup fails
     */
    @Transactional
    public Container createContainer(Sandbox sandbox, CreateContainerRequest request) {

        String dbName = request.getDatabaseName() != null && !request.getDatabaseName().isBlank()
                ? request.getDatabaseName()
                : "shadowbase_db";

        String username = request.getUsername() != null && !request.getUsername().isBlank()
                ? request.getUsername()
                : "shadowuser";

        String password = request.getPassword() != null && !request.getPassword().isBlank()
                ? request.getPassword()
                : "shadowpass";

        String imageName = request.getImageName() != null && !request.getImageName().isBlank()
                ? request.getImageName()
                : "mysql:8.4";

        MySQLContainer containerInstance = new MySQLContainer(DockerImageName.parse(imageName))
                .withDatabaseName(dbName)
                .withUsername(username)
                .withPassword(password);

        log.info("Starting MySQL container (image: {}) for sandbox {}...",
                imageName, sandbox != null ? sandbox.getId() : "none");

        try {
            containerInstance.start();
        } catch (Exception e) {
            log.error("Failed to start MySQL container: {}", e.getMessage(), e);

            try {
                containerInstance.stop();
            } catch (Exception ignored) {
            }

            Container failedContainer = Container.builder()
                    .id("failed-" + UUID.randomUUID())
                    .sandbox(sandbox)
                    .databaseType(DatabaseType.MYSQL)
                    .databaseName(dbName)
                    .username(username)
                    .password(password)
                    .imageName(imageName)
                    .credentialReference(request.getCredentialReference())
                    .status(ContainerStatus.FAILED)
                    .build();

            containerRepository.save(failedContainer);

            throw new ContainerOperationException("MySQL container startup failed: " + e.getMessage(), e);
        }

        String containerId = containerInstance.getContainerId();
        String host = containerInstance.getHost();
        Integer port = containerInstance.getFirstMappedPort();
        String jdbcUrl = containerInstance.getJdbcUrl();

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
                .databaseType(DatabaseType.MYSQL)
                .databaseName(dbName)
                .username(username)
                .password(password)
                .host(host)
                .port(port)
                .jdbcUrl(jdbcUrl)
                .imageName(containerInstance.getDockerImageName())
                .credentialReference(request.getCredentialReference())
                .dataSource(dataSource)
                .status(ContainerStatus.RUNNING)
                .build();

        log.info("MySQL container started. ID: {}, port: {}", containerId, port);

        return containerRepository.save(container);
    }

    /**
     * Starts an existing, stopped MySQL container using the Docker daemon directly.
     *
     * @param containerId the Docker container ID
     * @return updated Container entity in RUNNING status
     */
    @Transactional
    public Container startContainer(String containerId) {

        Container container = getContainer(containerId);

        if (container.getStatus() == ContainerStatus.RUNNING) {
            return container;
        }

        container.setStatus(ContainerStatus.STARTING);
        containerRepository.save(container);

        try {
            if (!isDockerAvailable()) {
                throw new ContainerOperationException("Docker daemon is not available.");
            }

            DockerClient client = getDockerClient();
            client.startContainerCmd(containerId).exec();

            updateMetadataFromDocker(container, client);

            container.setStatus(ContainerStatus.RUNNING);
            container.setStoppedAt(null);

            return containerRepository.save(container);

        } catch (Exception e) {
            log.error("Failed to start MySQL container {}: {}", containerId, e.getMessage(), e);

            container.setStatus(ContainerStatus.FAILED);
            containerRepository.save(container);

            throw new ContainerOperationException("Failed to start MySQL container: " + e.getMessage(), e);
        }
    }

    /**
     * Stops a running MySQL container with a grace period timeout.
     *
     * @param containerId the Docker container ID
     * @return updated Container entity in STOPPED status
     */
    @Transactional
    public Container stopContainer(String containerId) {

        Container container = getContainer(containerId);

        if (container.getStatus() == ContainerStatus.STOPPED) {
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

            return containerRepository.save(container);

        } catch (Exception e) {
            log.error("Failed to stop MySQL container {}: {}", containerId, e.getMessage(), e);

            container.setStatus(ContainerStatus.UNHEALTHY);
            containerRepository.save(container);

            throw new ContainerOperationException("Failed to stop MySQL container: " + e.getMessage(), e);
        }
    }

    /**
     * Forcefully removes a MySQL container from Docker and marks it as REMOVED in the database.
     *
     * @param containerId the Docker container ID
     */
    @Transactional
    public void removeContainer(String containerId) {

        Container container = getContainer(containerId);

        try {
            if (isDockerAvailable()) {
                DockerClient client = getDockerClient();

                try {
                    client.removeContainerCmd(containerId).withForce(true).exec();
                } catch (NotFoundException e) {
                    log.debug("MySQL container {} already removed in Docker.", containerId);
                }
            }
        } catch (Exception e) {
            log.warn("Error removing MySQL container {}: {}", containerId, e.getMessage());
        }

        container.setStatus(ContainerStatus.REMOVED);
        container.setStoppedAt(LocalDateTime.now());

        containerRepository.save(container);
    }

    /**
     * Retrieves a MySQL container by ID, ensuring it is indeed a MySQL instance.
     *
     * @param containerId the container identifier
     * @return matching Container entity
     * @throws ResourceNotFoundException   if container does not exist
     * @throws ContainerOperationException if container is not MySQL
     */
    @Transactional(readOnly = true)
    public Container getContainer(String containerId) {

        Container container = containerRepository.findById(containerId)
                .orElseThrow(() -> new ResourceNotFoundException("Container not found with ID: " + containerId));

        if (container.getDatabaseType() != DatabaseType.MYSQL) {
            throw new ContainerOperationException("Container " + containerId + " is not a MySQL container.");
        }

        return container;
    }

    /**
     * Lists all MySQL containers associated with a sandbox.
     *
     * @param sandboxId the sandbox UUID
     * @return list of matching MySQL Container entities
     */
    @Transactional(readOnly = true)
    public List<Container> getContainersBySandbox(UUID sandboxId) {

        return containerRepository.findBySandboxId(sandboxId)
                .stream()
                .filter(c -> c.getDatabaseType() == DatabaseType.MYSQL)
                .collect(Collectors.toList());
    }

    /**
     * Lists all MySQL container responses associated with a sandbox.
     *
     * @param sandboxId the sandbox UUID
     * @return list of secure ContainerResponse DTOs
     */
    @Transactional(readOnly = true)
    public List<ContainerResponse> getContainerResponsesBySandbox(UUID sandboxId) {

        return getContainersBySandbox(sandboxId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Startup listener that queries live Docker status for all candidate MySQL containers
     * and reconciles their persisted state in the database.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileContainers() {

        log.info("Reconciling MySQL containers against Docker daemon...");

        if (!isDockerAvailable()) {
            log.warn("Docker is unavailable. Skipping MySQL reconciliation.");
            return;
        }

        DockerClient client = getDockerClient();

        List<Container> candidates =
                containerRepository.findByDatabaseTypeAndStatusIn(
                        DatabaseType.MYSQL,
                        List.of(
                                ContainerStatus.RUNNING,
                                ContainerStatus.STARTING,
                                ContainerStatus.STOPPING
                        )
                );

        for (Container container : candidates) {
            try {
                InspectContainerResponse inspect = client.inspectContainerCmd(container.getId()).exec();

                if (inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning())) {
                    container.setStatus(ContainerStatus.RUNNING);
                    updateMetadataFromDocker(container, client);
                } else {
                    container.setStatus(ContainerStatus.STOPPED);
                    if (container.getStoppedAt() == null) {
                        container.setStoppedAt(LocalDateTime.now());
                    }
                }

            } catch (NotFoundException e) {
                container.setStatus(ContainerStatus.REMOVED);
                container.setStoppedAt(LocalDateTime.now());
            } catch (Exception e) {
                log.error("Error inspecting MySQL container {}: {}", container.getId(), e.getMessage());
                container.setStatus(ContainerStatus.UNHEALTHY);
            }

            containerRepository.save(container);
        }

        log.info("MySQL reconciliation completed for {} records.", candidates.size());
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
                            String hostPort = entry.getValue()[0].getHostPortSpec();

                            if (hostPort != null) {
                                int mappedPort = Integer.parseInt(hostPort);

                                container.setPort(mappedPort);
                                container.setHost(DockerClientFactory.instance().dockerHostIpAddress());
                                container.setJdbcUrl("jdbc:mysql://" + container.getHost()
                                        + ":" + mappedPort + "/" + container.getDatabaseName());
                                break;
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Could not refresh MySQL container metadata: {}", e.getMessage());
        }
    }
}
