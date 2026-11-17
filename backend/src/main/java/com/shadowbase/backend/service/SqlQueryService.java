package com.shadowbase.backend.service;

import java.util.UUID;

import com.shadowbase.backend.dto.SqlQueryRequest;
import com.shadowbase.backend.dto.SqlQueryResponse;

/**
 * Service for executing ad-hoc SQL queries and DDL statements
 * directly against a sandbox database container.
 */
public interface SqlQueryService {

    /**
     * Executes the specified SQL query or statement against a running container within the given sandbox.
     *
     * @param sandboxId UUID of the target sandbox
     * @param request   query parameters including SQL text, optional containerId, and row limits
     * @return SqlQueryResponse with columns, rows, execution timing, and status
     */
    SqlQueryResponse executeQuery(UUID sandboxId, SqlQueryRequest request);
}
