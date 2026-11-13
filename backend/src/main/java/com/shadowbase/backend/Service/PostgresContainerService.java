package com.shadowbase.backend.Service;

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
import com.shadowbase.backend.Dto.ContainerResponse;
import com.shadowbase.backend.Dto.CreateContainerRequest;
import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Exception.ContainerOperationException;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PostgresContainerService {

    private final ContainerRepository containerRepository;

    public PostgresContainerService(ContainerRepository containerRepository) {
        this.containerRepository = containerRepository;
    }

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

    @Transactional
    public Container createContainer(Sandbox sandbox, CreateContainerRequest request) {
        DatabaseType databaseType = DatabaseType.POSTGRESQL;

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

            throw new ContainerOperationException("Container startup failed: " + e.getMessage(), e);
        }

        String containerId = containerInstance.getContainerId();
        String host = containerInstance.getHost();
        Integer port = containerInstance.getFirstMappedPort();
        String jdbcUrl = containerInstance.getJdbcUrl();
        String actualImage = containerInstance.getDockerImageName();

        log.info("PostgreSQL container started successfully. ID: {}, Port: {}, JDBC URL: {}", 
                containerId, port, jdbcUrl);

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
                .status(ContainerStatus.RUNNING)
                .build();

        return containerRepository.save(container);
    }

    @Transactional
    public Container startContainer(String containerId) {
        Container container = getContainer(containerId);

        if (container.getStatus() == ContainerStatus.RUNNING) {
            log.info("Container {} is already RUNNING", containerId);
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
            log.info("Container {} started successfully", containerId);
            return containerRepository.save(container);
        } catch (Exception e) {
            log.error("Failed to start container {}: {}", containerId, e.getMessage(), e);
            container.setStatus(ContainerStatus.FAILED);
            containerRepository.save(container);
            throw new ContainerOperationException("Failed to start container: " + e.getMessage(), e);
        }
    }

    @Transactional
    public Container stopContainer(String containerId) {
        Container container = getContainer(containerId);

        if (container.getStatus() == ContainerStatus.STOPPED) {
            log.info("Container {} is already STOPPED", containerId);
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
            log.info("Container {} stopped successfully", containerId);
            return containerRepository.save(container);
        } catch (Exception e) {
            log.error("Failed to stop container {}: {}", containerId, e.getMessage(), e);
            container.setStatus(ContainerStatus.UNHEALTHY);
            containerRepository.save(container);
            throw new ContainerOperationException("Failed to stop container: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void removeContainer(String containerId) {
        Container container = getContainer(containerId);

        log.info("Removing container {}...", containerId);
        try {
            if (isDockerAvailable()) {
                DockerClient client = getDockerClient();
                try {
                    client.removeContainerCmd(containerId).withForce(true).exec();
                } catch (NotFoundException e) {
                    log.debug("Container {} already removed in Docker", containerId);
                }
            }
        } catch (Exception e) {
            log.warn("Non-fatal error removing Docker container {}: {}", containerId, e.getMessage());
        }

        container.setStatus(ContainerStatus.REMOVED);
        container.setStoppedAt(LocalDateTime.now());
        containerRepository.save(container);
        log.info("Container {} marked as REMOVED", containerId);
    }

    @Transactional(readOnly = true)
    public Container getContainer(String containerId) {
        return containerRepository.findById(containerId)
                .orElseThrow(() -> new ResourceNotFoundException("Container not found with ID: " + containerId));
    }

    @Transactional(readOnly = true)
    public ContainerResponse getContainerDetails(String containerId) {
        return mapToResponse(getContainer(containerId));
    }

    @Transactional(readOnly = true)
    public List<Container> getContainersBySandbox(UUID sandboxId) {
        return containerRepository.findBySandboxId(sandboxId);
    }

    @Transactional(readOnly = true)
    public List<ContainerResponse> getContainerResponsesBySandbox(UUID sandboxId) {
        return getContainersBySandbox(sandboxId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileContainers() {
        log.info("Reconciling PostgreSQL containers against Docker daemon...");
        if (!isDockerAvailable()) {
            log.warn("Docker is not reachable. Skipping startup reconciliation.");
            return;
        }

        DockerClient client = getDockerClient();
        List<Container> candidateContainers = containerRepository.findByStatusIn(
                List.of(ContainerStatus.RUNNING, ContainerStatus.STARTING, ContainerStatus.STOPPING)
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
                log.warn("Container {} not found in Docker. Marking REMOVED.", c.getId());
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

        log.info("Container reconciliation completed for {} records.", candidateContainers.size());
    }

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
            log.warn("Could not refresh Docker network settings for container {}: {}", container.getId(), e.getMessage());
        }
    }
}
