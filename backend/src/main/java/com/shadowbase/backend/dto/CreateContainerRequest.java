package com.shadowbase.backend.dto;

import java.util.UUID;

import com.shadowbase.backend.Entity.Container.DatabaseType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for provisioning a new database container within a Sandbox.
 * <p>
 * Contains specifications for the database engine (PostgreSQL or MySQL), credentials,
 * database name, and optional custom Docker image.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContainerRequest {

    /**
     * Unique identifier of the Sandbox environment that will host this container.
     */
    private UUID sandboxId;

    /**
     * Database engine type (POSTGRESQL or MYSQL).
     */
    private DatabaseType databaseType;

    /**
     * Name of the initial database to create inside the container.
     */
    private String databaseName;

    /**
     * Administrative username for database access.
     */
    private String username;

    /**
     * Plaintext administrative password passed only at creation time (never stored plaintext in DB).
     */
    private String password;

    /**
     * Optional custom Docker image tag (defaults to official image if omitted).
     */
    private String imageName;

    /**
     * Optional reference key or vault alias for credential retrieval.
     */
    private String credentialReference;

    /**
     * Optional ID of the source DataSource associated with this container.
     */
    private UUID dataSourceId;
}
