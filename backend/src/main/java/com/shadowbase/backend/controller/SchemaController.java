package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shadowbase.backend.dto.SchemaImportResponse;
import com.shadowbase.backend.service.SchemaService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller providing endpoints for importing SQL schemas and querying
 * schema initialization history for ShadowBase sandbox database containers.
 * <p>
 * Ensures that a single container contains only one database and one schema.
 * Supports both sandbox-level and container-targeted schema operations.
 * <p>
 * Base URIs:
 * <ul>
 *   <li>{@code /api/sandboxes/{sandboxId}/schema}</li>
 *   <li>{@code /api/sandboxes/{sandboxId}/containers/{containerId}/schema}</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping
public class SchemaController {

    private final SchemaService schemaService;

    public SchemaController(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * Uploads and executes an SQL schema script against a sandbox database container.
     * Optionally targets a specific container by passing 'containerId'.
     *
     * @param sandboxId   UUID of the target sandbox
     * @param containerId optional Docker container ID targeting a specific database container
     * @param file        uploaded SQL schema file (multipart/form-data)
     * @param userId      optional requesting user identifier for authorization
     * @return 201 CREATED with SchemaImportResponse
     */
    @PostMapping(value = {
            "/api/sandboxes/{sandboxId}/schema/import",
            "/api/sandboxes/{sandboxId}/containers/{containerId}/schema/import"
    }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SchemaImportResponse> importSchema(
            @PathVariable("sandboxId") UUID sandboxId,
            @PathVariable(value = "containerId", required = false) String pathContainerId,
            @RequestParam(value = "containerId", required = false) String queryContainerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) UUID userId) {

        String targetContainerId = pathContainerId != null ? pathContainerId : queryContainerId;

        log.info("REST request to import schema for sandbox: {}, container: {}, file: {}",
                sandboxId, targetContainerId != null ? targetContainerId : "default",
                file != null ? file.getOriginalFilename() : "null");

        SchemaImportResponse response = schemaService.importSchema(sandboxId, targetContainerId, file, userId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieves the latest schema import status for the requested sandbox or container.
     *
     * @param sandboxId   UUID of the sandbox
     * @param containerId optional Docker container ID
     * @return 200 OK with the latest SchemaImportResponse, or 204 NO_CONTENT if none exists
     */
    @GetMapping({
            "/api/sandboxes/{sandboxId}/schema/status",
            "/api/sandboxes/{sandboxId}/containers/{containerId}/schema/status"
    })
    public ResponseEntity<SchemaImportResponse> getLatestImportStatus(
            @PathVariable("sandboxId") UUID sandboxId,
            @PathVariable(value = "containerId", required = false) String pathContainerId,
            @RequestParam(value = "containerId", required = false) String queryContainerId) {

        String targetContainerId = pathContainerId != null ? pathContainerId : queryContainerId;
        SchemaImportResponse response = schemaService.getLatestImportStatus(sandboxId, targetContainerId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves previous schema import attempts associated with the sandbox or container.
     *
     * @param sandboxId   UUID of the sandbox
     * @param containerId optional Docker container ID
     * @return 200 OK with list of previous SchemaImportResponse objects
     */
    @GetMapping({
            "/api/sandboxes/{sandboxId}/schema/imports",
            "/api/sandboxes/{sandboxId}/containers/{containerId}/schema/imports"
    })
    public ResponseEntity<List<SchemaImportResponse>> getImportHistory(
            @PathVariable("sandboxId") UUID sandboxId,
            @PathVariable(value = "containerId", required = false) String pathContainerId,
            @RequestParam(value = "containerId", required = false) String queryContainerId) {

        String targetContainerId = pathContainerId != null ? pathContainerId : queryContainerId;
        return ResponseEntity.ok(schemaService.getImportHistory(sandboxId, targetContainerId));
    }
}
