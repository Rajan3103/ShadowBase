package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Diagnostic response for testing database connectivity and credentials
 * against an external source DataSource.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceTestResponse {

    private boolean success;
    private String message;
    private String databaseType;
    private long latencyMs;
    private String host;
    private int port;
    private String databaseName;
}
