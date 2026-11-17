package com.shadowbase.backend.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result payload returned from executing an ad-hoc SQL query against a sandbox container.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlQueryResponse {

    /**
     * Column headers/names returned by the query.
     */
    private List<String> columns;

    /**
     * 2D list of row values matching the columns.
     */
    private List<List<Object>> rows;

    /**
     * Total number of rows returned or affected.
     */
    private int rowCount;

    /**
     * Execution duration in milliseconds.
     */
    private long executionTimeMs;

    /**
     * Human-readable status or execution summary message.
     */
    private String message;

    /**
     * Indicates whether the statement produced a tabular result set (e.g. SELECT) or an update count.
     */
    private boolean hasResultSet;
}
