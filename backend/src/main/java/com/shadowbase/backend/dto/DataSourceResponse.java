package com.shadowbase.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response payload representing an external source database connection configuration.
 * Passwords and sensitive credentials are intentionally excluded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceResponse {

    /**
     * Unique identifier for the DataSource configuration.
     */
    private UUID id;

    /**
     * Logical display name for this data source connection.
     */
    private String connectionName;

    /**
     * Hostname or IP address of the source database server.
     */
    private String host;

    /**
     * Port number where the source database is reachable.
     */
    private Integer port;

    /**
     * Logical database name on the source server.
     */
    private String databaseName;

    /**
     * Database user account for connecting to the source.
     */
    private String databaseUsername;

    /**
     * Docker container ID of the linked sandbox container, if currently mapped.
     */
    private String containerId;

    /**
     * Timestamp when this data source record was created.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when this data source record was last updated.
     */
    private LocalDateTime updatedAt;
}
