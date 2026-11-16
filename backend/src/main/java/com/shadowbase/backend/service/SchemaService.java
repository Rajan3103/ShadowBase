package com.shadowbase.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.shadowbase.backend.dto.SchemaImportResponse;

/**
 * Service contract for importing and executing SQL schema definitions
 * against sandbox database containers.
 * <p>
 * Enforces that a single container contains only one database and has only one schema.
 */
public interface SchemaService {

    /**
     * Imports and executes an uploaded SQL file into a sandbox's running database container.
     * If containerId is omitted, resolves the active database container for the sandbox.
     *
     * @param sandboxId        UUID of the target sandbox
     * @param containerId      optional Docker container ID targeting a specific database container
     * @param file             uploaded SQL script multipart file
     * @param requestingUserId optional UUID of the requesting user for authorization checks
     * @return response details containing import status and metadata
     */
    SchemaImportResponse importSchema(UUID sandboxId, String containerId, MultipartFile file, UUID requestingUserId);

    /**
     * Convenience overload importing schema into the active database container of the sandbox.
     *
     * @param sandboxId        UUID of the target sandbox
     * @param file             uploaded SQL script multipart file
     * @param requestingUserId optional UUID of the requesting user
     * @return response details containing import status and metadata
     */
    SchemaImportResponse importSchema(UUID sandboxId, MultipartFile file, UUID requestingUserId);

    /**
     * Retrieves the latest schema import attempt for a given sandbox, optionally filtered by container.
     *
     * @param sandboxId   UUID of the sandbox
     * @param containerId optional Docker container ID
     * @return latest SchemaImportResponse details, or null if none
     */
    SchemaImportResponse getLatestImportStatus(UUID sandboxId, String containerId);

    /**
     * Retrieves the latest schema import attempt for a given sandbox.
     *
     * @param sandboxId UUID of the sandbox
     * @return latest SchemaImportResponse details, or null if none
     */
    SchemaImportResponse getLatestImportStatus(UUID sandboxId);

    /**
     * Retrieves the complete schema import history for a sandbox, optionally filtered by container.
     *
     * @param sandboxId   UUID of the sandbox
     * @param containerId optional Docker container ID
     * @return chronological list of previous import attempts
     */
    List<SchemaImportResponse> getImportHistory(UUID sandboxId, String containerId);

    /**
     * Retrieves the complete schema import history for a sandbox.
     *
     * @param sandboxId UUID of the sandbox
     * @return chronological list of previous import attempts
     */
    List<SchemaImportResponse> getImportHistory(UUID sandboxId);

    /**
     * Retrieves the latest schema import for a specific container directly.
     *
     * @param containerId Docker container ID
     * @return latest SchemaImportResponse details, or null if none
     */
    SchemaImportResponse getContainerLatestImportStatus(String containerId);

    /**
     * Retrieves the complete schema import history for a specific container directly.
     *
     * @param containerId Docker container ID
     * @return chronological list of previous import attempts for this container
     */
    List<SchemaImportResponse> getContainerImportHistory(String containerId);
}

