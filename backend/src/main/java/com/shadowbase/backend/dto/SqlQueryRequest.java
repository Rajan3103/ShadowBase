package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for executing an ad-hoc SQL query against a sandbox container database.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlQueryRequest {

    /**
     * The SQL statement(s) to execute (e.g. SELECT, INSERT, UPDATE, DDL).
     */
    private String sql;

    /**
     * Optional target container ID within the sandbox. If omitted, defaults to the first active running container.
     */
    private String containerId;

    /**
     * Maximum number of result rows to return (default: 1000, capped at 5000).
     */
    @Builder.Default
    private Integer maxRows = 1000;
}
