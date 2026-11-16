package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shadowbase.backend.dto.ContainerResponse;
import com.shadowbase.backend.dto.CreateContainerRequest;
import com.shadowbase.backend.dto.CreateSandboxRequest;
import com.shadowbase.backend.dto.SandboxResponse;
import com.shadowbase.backend.service.SandboxService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller exposing management endpoints for Sandbox environments and their associated containers.
 * <p>
 * Base URI: {@code /api/sandboxes}
 */
@Slf4j
@RestController
@RequestMapping("/api/sandboxes")
public class SandboxController {

    private final SandboxService sandboxService;

    /**
     * Constructor injection for the SandboxService.
     *
     * @param sandboxService service handling sandbox operations
     */
    public SandboxController(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    /**
     * Creates a new sandbox environment.
     *
     * @param request payload containing sandbox configuration and optional initial containers
     * @return 201 CREATED with the provisioned SandboxResponse
     */
    @PostMapping
    public ResponseEntity<SandboxResponse> createSandbox(
            @RequestBody CreateSandboxRequest request) {

        log.info("REST request to create sandbox: {}", request.getName());
        SandboxResponse response = sandboxService.createSandbox(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves sandbox details by ID.
     *
     * @param id UUID of the sandbox
     * @return 200 OK with SandboxResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<SandboxResponse> getSandbox(
            @PathVariable("id") UUID id) {

        return ResponseEntity.ok(sandboxService.getSandbox(id));
    }

    /**
     * Lists all sandboxes owned by a specific user.
     *
     * @param userId UUID of the user
     * @return 200 OK with list of SandboxResponse objects
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SandboxResponse>> listSandboxesByUser(
            @PathVariable("userId") UUID userId) {

        return ResponseEntity.ok(sandboxService.listSandboxesByUser(userId));
    }

    /**
     * Starts all containers associated with a sandbox.
     *
     * @param id UUID of the sandbox
     * @return 200 OK with updated SandboxResponse
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<SandboxResponse> startSandbox(
            @PathVariable("id") UUID id) {

        log.info("REST request to start sandbox: {}", id);
        return ResponseEntity.ok(sandboxService.startSandbox(id));
    }

    /**
     * Stops all active containers in a sandbox.
     *
     * @param id UUID of the sandbox
     * @return 200 OK with updated SandboxResponse in STOPPED status
     */
    @PostMapping("/{id}/stop")
    public ResponseEntity<SandboxResponse> stopSandbox(
            @PathVariable("id") UUID id) {

        log.info("REST request to stop sandbox: {}", id);
        return ResponseEntity.ok(sandboxService.stopSandbox(id));
    }

    /**
     * Deletes a sandbox and cleans up all its underlying Docker container resources.
     *
     * @param id UUID of the sandbox
     * @return 204 NO_CONTENT
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSandbox(
            @PathVariable("id") UUID id) {

        log.info("REST request to delete sandbox: {}", id);
        sandboxService.deleteSandbox(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Adds an additional database container (PostgreSQL or MySQL) to an existing sandbox.
     *
     * @param id      UUID of the sandbox
     * @param request container configuration parameters
     * @return 201 CREATED with the provisioned ContainerResponse
     */
    @PostMapping("/{id}/containers")
    public ResponseEntity<ContainerResponse> addContainer(
            @PathVariable("id") UUID id,
            @RequestBody CreateContainerRequest request) {

        log.info("REST request to add container to sandbox: {}", id);
        ContainerResponse response = sandboxService.addContainerToSandbox(id, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves all containers currently provisioned within the specified sandbox.
     *
     * @param id UUID of the sandbox
     * @return 200 OK with list of ContainerResponse objects
     */
    @GetMapping("/{id}/containers")
    public ResponseEntity<List<ContainerResponse>> getContainers(
            @PathVariable("id") UUID id) {

        return ResponseEntity.ok(
                sandboxService.getSandbox(id).getContainers()
        );
    }
}
