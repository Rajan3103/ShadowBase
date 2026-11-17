package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating or registering an external source database configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDataSourceRequest {

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
     * Database password for source database access.
     */
    private String databasePassword;
}
