package com.shadowbase.backend.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shadowbase.backend.dto.SqlQueryRequest;
import com.shadowbase.backend.dto.SqlQueryResponse;
import com.shadowbase.backend.service.SqlQueryService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller exposing SQL query execution endpoints for sandbox containers.
 * <p>
 * Allows the frontend SQL Editor to execute queries and DDL statements
 * directly against active sandbox databases.
 */
@Slf4j
@RestController
@RequestMapping
public class SqlQueryController {

    private final SqlQueryService sqlQueryService;

    public SqlQueryController(SqlQueryService sqlQueryService) {
        this.sqlQueryService = sqlQueryService;
    }

    /**
     * Executes an ad-hoc SQL query against the specified sandbox (targeting the active running container).
     *
     * @param sandboxId UUID of the target sandbox
     * @param request   payload containing the SQL query and optional limits
     * @return 200 OK with columns, rows, execution timing, and status
     */
    @PostMapping("/api/sandboxes/{sandboxId}/query")
    public ResponseEntity<SqlQueryResponse> executeSandboxQuery(
            @PathVariable("sandboxId") UUID sandboxId,
            @RequestBody SqlQueryRequest request) {

        log.info("REST request to execute SQL query on sandbox: {}", sandboxId);
        SqlQueryResponse response = sqlQueryService.executeQuery(sandboxId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Executes an ad-hoc SQL query targeted to a specific container within a sandbox.
     *
     * @param sandboxId   UUID of the target sandbox
     * @param containerId Docker container ID
     * @param request     payload containing the SQL query
     * @return 200 OK with columns, rows, execution timing, and status
     */
    @PostMapping("/api/sandboxes/{sandboxId}/containers/{containerId}/query")
    public ResponseEntity<SqlQueryResponse> executeContainerQuery(
            @PathVariable("sandboxId") UUID sandboxId,
            @PathVariable("containerId") String containerId,
            @RequestBody SqlQueryRequest request) {

        log.info("REST request to execute SQL query on container: {} in sandbox: {}", containerId, sandboxId);
        if (request == null) {
            request = new SqlQueryRequest();
        }
        request.setContainerId(containerId);
        SqlQueryResponse response = sqlQueryService.executeQuery(sandboxId, request);
        return ResponseEntity.ok(response);
    }
}
