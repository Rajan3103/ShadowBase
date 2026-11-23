package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed SQL query or replay execution error for the error console.
 * Matched to the frontend ErrorConsole contract: { id, ts, table, operation, sqlState, message }.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayErrorDto {

    private String id;
    private long ts;
    private String table;
    private String operation;
    private String sqlState;
    private String message;
}
