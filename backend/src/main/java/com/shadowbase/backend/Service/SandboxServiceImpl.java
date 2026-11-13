package com.shadowbase.backend.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shadowbase.backend.Dto.ContainerResponse;
import com.shadowbase.backend.Dto.CreateContainerRequest;
import com.shadowbase.backend.Dto.CreateSandboxRequest;
import com.shadowbase.backend.Dto.SandboxResponse;
import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.DataSource;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.User;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Exception.SandboxOperationException;
import com.shadowbase.backend.Repository.DatasourceRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.Repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SandboxServiceImpl implements SandboxService {

    private final SandboxRepository sandboxRepository;
    private final PostgresContainerService postgresContainerService;
    private final UserRepository userRepository;
    private final DatasourceRepository datasourceRepository;

    public SandboxServiceImpl(
            SandboxRepository sandboxRepository,
            PostgresContainerService postgresContainerService,
            UserRepository userRepository,
            DatasourceRepository datasourceRepository) {
        this.sandboxRepository = sandboxRepository;
        this.postgresContainerService = postgresContainerService;
        this.userRepository = userRepository;
        this.datasourceRepository = datasourceRepository;
    }

    @Override
    @Transactional
    public SandboxResponse createSandbox(CreateSandboxRequest request) {
        log.info("Creating new sandbox '{}' for userId {}", request.getName(), request.getUserId());

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Sandbox name must not be empty");
        }

        User user = null;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + request.getUserId()));
        }

        List<DataSource> dataSources = new ArrayList<>();
        if (request.getDataSourceIds() != null && !request.getDataSourceIds().isEmpty()) {
            dataSources = datasourceRepository.findAllById(request.getDataSourceIds());
        }

        Sandbox sandbox = Sandbox.builder()
                .name(request.getName())
                .user(user)
                .dataSources(dataSources)
                .status(SandboxStatus.CREATING)
                .containers(new ArrayList<>())
                .build();

        sandbox = sandboxRepository.save(sandbox);
        log.info("Sandbox '{}' persisted with ID: {} and status: CREATING", sandbox.getName(), sandbox.getId());

        if (request.getInitialContainers() != null && !request.getInitialContainers().isEmpty()) {
            for (CreateContainerRequest containerReq : request.getInitialContainers()) {
                try {
                    containerReq.setSandboxId(sandbox.getId());
                    Container createdContainer = postgresContainerService.createContainer(sandbox, containerReq);
                    sandbox.getContainers().add(createdContainer);
                } catch (Exception e) {
                    log.error("Failed to provision container for sandbox {}: {}", sandbox.getId(), e.getMessage(), e);
                    sandbox.setStatus(SandboxStatus.FAILED);
                    sandboxRepository.save(sandbox);
                    throw new SandboxOperationException("Sandbox container provisioning failed: " + e.getMessage(), e);
                }
            }
            updateSandboxReadiness(sandbox);
        } else {
            sandbox.setStatus(SandboxStatus.READY);
        }

        sandbox = sandboxRepository.save(sandbox);
        return mapToResponse(sandbox);
    }

    @Override
    @Transactional(readOnly = true)
    public SandboxResponse getSandbox(UUID sandboxId) {
        Sandbox sandbox = findSandboxById(sandboxId);
        return mapToResponse(sandbox);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SandboxResponse> listSandboxesByUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with ID: " + userId);
        }
        return sandboxRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SandboxResponse startSandbox(UUID sandboxId) {
        Sandbox sandbox = findSandboxById(sandboxId);

        if (sandbox.getStatus() == SandboxStatus.DESTROYED) {
            throw new SandboxOperationException("Cannot start destroyed sandbox: " + sandboxId);
        }

        log.info("Starting sandbox {} and its associated containers...", sandboxId);
        sandbox.setStatus(SandboxStatus.STARTING);
        sandboxRepository.save(sandbox);

        List<Container> containers = sandbox.getContainers();
        for (Container c : containers) {
            try {
                postgresContainerService.startContainer(c.getId());
            } catch (Exception e) {
                log.error("Failed to start container {} during sandbox start: {}", c.getId(), e.getMessage());
            }
        }

        updateSandboxReadiness(sandbox);
        Sandbox saved = sandboxRepository.save(sandbox);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public SandboxResponse stopSandbox(UUID sandboxId) {
        Sandbox sandbox = findSandboxById(sandboxId);

        log.info("Stopping sandbox {} and its associated containers...", sandboxId);
        sandbox.setStatus(SandboxStatus.STOPPING);
        sandboxRepository.save(sandbox);

        List<Container> containers = sandbox.getContainers();
        for (Container c : containers) {
            try {
                postgresContainerService.stopContainer(c.getId());
            } catch (Exception e) {
                log.error("Failed to stop container {} during sandbox stop: {}", c.getId(), e.getMessage());
            }
        }

        sandbox.setStatus(SandboxStatus.STOPPED);
        Sandbox saved = sandboxRepository.save(sandbox);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void deleteSandbox(UUID sandboxId) {
        Sandbox sandbox = findSandboxById(sandboxId);

        log.info("Deleting sandbox {} and removing its containers...", sandboxId);
        sandbox.setStatus(SandboxStatus.STOPPING);
        sandboxRepository.save(sandbox);

        List<Container> containers = sandbox.getContainers();
        for (Container c : containers) {
            try {
                postgresContainerService.removeContainer(c.getId());
            } catch (Exception e) {
                log.error("Failed to remove container {} during sandbox deletion: {}", c.getId(), e.getMessage());
            }
        }

        sandbox.setStatus(SandboxStatus.DESTROYED);
        sandboxRepository.save(sandbox);
        log.info("Sandbox {} metadata preserved with status DESTROYED", sandboxId);
    }

    @Override
    @Transactional
    public ContainerResponse addContainerToSandbox(UUID sandboxId, CreateContainerRequest request) {
        Sandbox sandbox = findSandboxById(sandboxId);

        if (sandbox.getStatus() == SandboxStatus.DESTROYED) {
            throw new SandboxOperationException("Cannot add container to destroyed sandbox: " + sandboxId);
        }

        request.setSandboxId(sandboxId);
        Container container = postgresContainerService.createContainer(sandbox, request);

        if (sandbox.getContainers() == null) {
            sandbox.setContainers(new ArrayList<>());
        }
        sandbox.getContainers().add(container);

        updateSandboxReadiness(sandbox);
        sandboxRepository.save(sandbox);

        return postgresContainerService.mapToResponse(container);
    }

    @Override
    public SandboxResponse mapToResponse(Sandbox sandbox) {
        if (sandbox == null) {
            return null;
        }

        List<ContainerResponse> containerResponses = new ArrayList<>();
        if (sandbox.getContainers() != null) {
            containerResponses = sandbox.getContainers().stream()
                    .map(postgresContainerService::mapToResponse)
                    .collect(Collectors.toList());
        }

        List<UUID> dataSourceIds = new ArrayList<>();
        if (sandbox.getDataSources() != null) {
            dataSourceIds = sandbox.getDataSources().stream()
                    .map(DataSource::getId)
                    .collect(Collectors.toList());
        }

        return SandboxResponse.builder()
                .id(sandbox.getId())
                .name(sandbox.getName())
                .userId(sandbox.getUser() != null ? sandbox.getUser().getId() : null)
                .dataSourceIds(dataSourceIds)
                .status(sandbox.getStatus())
                .containers(containerResponses)
                .createdAt(sandbox.getCreatedAt())
                .updatedAt(sandbox.getUpdatedAt())
                .build();
    }

    private Sandbox findSandboxById(UUID sandboxId) {
        return sandboxRepository.findById(sandboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Sandbox not found with ID: " + sandboxId));
    }

    private void updateSandboxReadiness(Sandbox sandbox) {
        List<Container> containers = sandbox.getContainers();
        if (containers == null || containers.isEmpty()) {
            sandbox.setStatus(SandboxStatus.READY);
            return;
        }

        long runningCount = containers.stream()
                .filter(c -> c.getStatus() == ContainerStatus.RUNNING)
                .count();

        long failedOrUnhealthyCount = containers.stream()
                .filter(c -> c.getStatus() == ContainerStatus.FAILED || c.getStatus() == ContainerStatus.UNHEALTHY)
                .count();

        if (runningCount == containers.size()) {
            sandbox.setStatus(SandboxStatus.READY);
        } else if (runningCount > 0) {
            sandbox.setStatus(SandboxStatus.DEGRADED);
        } else if (failedOrUnhealthyCount == containers.size()) {
            sandbox.setStatus(SandboxStatus.FAILED);
        } else {
            sandbox.setStatus(SandboxStatus.STARTING);
        }
    }
}
