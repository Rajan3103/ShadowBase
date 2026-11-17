package com.shadowbase.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.SchemaStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) returning the status and details of an SQL schema import execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaImportResponse {

    /**
     * Unique identifier of the schema import record.
     */
    private UUID id;

    /**
     * Unique identifier of the parent Sandbox environment.
     */
    private UUID sandboxId;

    /**
     * Docker container ID of the database instance targeted by the import.
     */
    private String containerId;

    /**
     * Original filename of the uploaded SQL script.
     */
    private String fileName;

    /**
     * Engine type of the database container (POSTGRESQL or MYSQL).
     */
    private DatabaseType databaseType;

    /**
     * SHA-256 cryptographic checksum of the uploaded SQL file.
     */
    private String checksum;

    /**
     * Current execution status of the import operation (IN_PROGRESS, SUCCESS, FAILED).
     */
    private SchemaStatus status;

    /**
     * Timestamp when the import execution was initiated.
     */
    private LocalDateTime startedAt;

    /**
     * Timestamp when the import execution finished (if completed).
     */
    private LocalDateTime completedAt;

    /**
     * Sanitized error summary if the execution failed; null upon success.
     */
    private String errorMessage;
}
