package com.shadowbase.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.SchemaStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing container metadata returned to API clients.
 * <p>
 * Exposes dynamically mapped ports, connection URLs, engine type, and operational status.
 * Note: Password fields are strictly omitted from responses for security.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerResponse {

    /**
     * Unique identifier of the container (UUID string or Docker container ID).
     */
    private String id;

    /**
     * Unique identifier of the parent Sandbox environment.
     */
    private UUID sandboxId;

    /**
     * Docker image name and tag used to run the container.
     */
    private String imageName;

    /**
     * Reachable host IP or hostname (e.g., localhost).
     */
    private String host;

    /**
     * Dynamically mapped external port on the host machine.
     */
    private Integer port;

    /**
     * Complete JDBC connection URL for direct database client connections.
     */
    private String jdbcUrl;

    /**
     * Database user account name.
     */
    private String username;

    /**
     * Database name created within the container.
     */
    private String databaseName;

    /**
     * Database engine type (POSTGRESQL or MYSQL).
     */
    private DatabaseType databaseType;

    /**
     * Current lifecycle/operational status of the container.
     */
    private ContainerStatus status;

    /**
     * Schema initialization status of the database in this container.
     */
    private SchemaStatus schemaStatus;

    /**
     * Secret reference identifier for stored credentials.
     */
    private String credentialReference;

    /**
     * ID of the associated source DataSource if linked.
     */
    private UUID dataSourceId;

    /**
     * Timestamp when the container record was created.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when the container record was last updated.
     */
    private LocalDateTime updatedAt;

    /**
     * Optional expiration timestamp for ephemeral sandboxes.
     */
    private LocalDateTime expiresAt;

    /**
     * Timestamp when the container was stopped, if applicable.
     */
    private LocalDateTime stoppedAt;
}
