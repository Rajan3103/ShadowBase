package com.shadowbase.backend.service;

import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.dto.ContainerResponse;
import com.shadowbase.backend.dto.CreateContainerRequest;
import com.shadowbase.backend.dto.CreateSandboxRequest;
import com.shadowbase.backend.dto.SandboxResponse;

/**
 * Service contract for managing multi-container Sandbox environments in ShadowBase.
 * <p>
 * A Sandbox represents an isolated developer testing workspace that contains multiple
 * database containers (both PostgreSQL and MySQL). DataSources are linked exclusively
 * to individual database containers, independent of Sandbox.
 */
public interface SandboxService {

    /**
     * Creates and initializes a new Sandbox environment.
     * Optionally provisions initial database containers and updates overall readiness.
     *
     * @param request creation parameters (name, userId, initialContainers)
     * @return the created Sandbox response DTO
     */
    SandboxResponse createSandbox(CreateSandboxRequest request);

    /**
     * Retrieves details for a specific sandbox by its UUID.
     *
     * @param sandboxId unique identifier of the sandbox
     * @return Sandbox response DTO
     */
    SandboxResponse getSandbox(UUID sandboxId);

    /**
     * Lists all sandboxes owned by a specific user.
     *
     * @param userId unique identifier of the user
     * @return list of Sandbox response DTOs
     */
    List<SandboxResponse> listSandboxesByUser(UUID userId);

    /**
     * Starts all database containers associated with a sandbox and updates its readiness status.
     *
     * @param sandboxId unique identifier of the sandbox
     * @return updated Sandbox response DTO
     */
    SandboxResponse startSandbox(UUID sandboxId);

    /**
     * Stops all active database containers associated with a sandbox.
     *
     * @param sandboxId unique identifier of the sandbox
     * @return updated Sandbox response DTO in STOPPED status
     */
    SandboxResponse stopSandbox(UUID sandboxId);

    /**
     * Deletes a sandbox by coordinating the removal of all its underlying Docker containers
     * while preserving sandbox metadata in DESTROYED status for auditability.
     *
     * @param sandboxId unique identifier of the sandbox
     */
    void deleteSandbox(UUID sandboxId);

    /**
     * Adds an additional database container (PostgreSQL or MySQL) to an existing sandbox.
     *
     * @param sandboxId the sandbox identifier
     * @param request   configuration parameters for the new container
     * @return ContainerResponse representing the newly provisioned container
     */
    ContainerResponse addContainerToSandbox(UUID sandboxId, CreateContainerRequest request);

    /**
     * Maps an internal Sandbox entity to an external, client-safe SandboxResponse DTO.
     *
     * @param sandbox the Sandbox entity
     * @return mapped SandboxResponse DTO
     */
    SandboxResponse mapToResponse(Sandbox sandbox);
}
