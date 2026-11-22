package com.shadowbase.backend.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Exception.SandboxOperationException;
import com.shadowbase.backend.Exception.SqlQueryException;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.dto.SqlQueryRequest;
import com.shadowbase.backend.dto.SqlQueryResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link SqlQueryService} that uses {@link ContainerConnectionProvider}
 * to execute queries directly against sandbox database containers.
 */
@Slf4j
@Service
public class SqlQueryServiceImpl implements SqlQueryService {

    private static final int DEFAULT_MAX_ROWS = 1000;
    private static final int MAX_ALLOWED_ROWS = 5000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final SandboxRepository sandboxRepository;
    private final ContainerRepository containerRepository;
    private final ContainerConnectionProvider connectionProvider;
    private final MetricsService metricsService;

    public SqlQueryServiceImpl(
            SandboxRepository sandboxRepository,
            ContainerRepository containerRepository,
            ContainerConnectionProvider connectionProvider) {
        this(sandboxRepository, containerRepository, connectionProvider, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SqlQueryServiceImpl(
            SandboxRepository sandboxRepository,
            ContainerRepository containerRepository,
            ContainerConnectionProvider connectionProvider,
            MetricsService metricsService) {
        this.sandboxRepository = sandboxRepository;
        this.containerRepository = containerRepository;
        this.connectionProvider = connectionProvider;
        this.metricsService = metricsService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SqlQueryResponse executeQuery(UUID sandboxId, SqlQueryRequest request) {
        if (sandboxId == null) {
            throw new IllegalArgumentException("Sandbox ID must not be null");
        }
        if (request == null || request.getSql() == null || request.getSql().trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query must not be blank");
        }

        Sandbox sandbox = sandboxRepository.findById(sandboxId)
                .orElseThrow(() -> new ResourceNotFoundException("Sandbox not found with ID: " + sandboxId));

        if (sandbox.getStatus() == SandboxStatus.DESTROYED) {
            throw new SandboxOperationException("Cannot execute query against destroyed sandbox: " + sandboxId);
        }

        Container container = resolveContainer(sandbox, request.getContainerId());

        String sql = request.getSql().trim();
        int maxRows = (request.getMaxRows() != null && request.getMaxRows() > 0)
                ? Math.min(request.getMaxRows(), MAX_ALLOWED_ROWS)
                : DEFAULT_MAX_ROWS;

        log.info("Executing SQL query for sandbox '{}' (container: {}): {}",
                sandboxId, container.getId(), sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);

        long startTime = System.currentTimeMillis();

        try (Connection connection = connectionProvider.openConnection(container);
             Statement statement = connection.createStatement()) {

            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            boolean hasResultSet = statement.execute(sql);
            long executionTimeMs = System.currentTimeMillis() - startTime;

            if (hasResultSet) {
                try (ResultSet rs = statement.getResultSet()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    List<String> columns = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(metaData.getColumnLabel(i));
                    }

                    List<List<Object>> rows = new ArrayList<>();
                    int rowCount = 0;
                    while (rs.next() && rowCount < maxRows) {
                        List<Object> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            Object val = rs.getObject(i);
                            row.add(formatValue(val));
                        }
                        rows.add(row);
                        rowCount++;
                    }

                    String summary = String.format("Query executed successfully in %d ms (%d row(s) returned)",
                            executionTimeMs, rows.size());

                    if (metricsService != null) {
                        metricsService.recordExecution(true, extractTable(sql), extractOperation(sql), null, null);
                    }

                    return SqlQueryResponse.builder()
                            .columns(columns)
                            .rows(rows)
                            .rowCount(rows.size())
                            .executionTimeMs(executionTimeMs)
                            .message(summary)
                            .hasResultSet(true)
                            .build();
                }
            } else {
                int updateCount = statement.getUpdateCount();
                int effectiveCount = Math.max(updateCount, 0);
                List<String> columns = Collections.singletonList("affected_rows");
                List<List<Object>> rows = Collections.singletonList(Collections.singletonList(effectiveCount));

                String summary = String.format("Statement executed successfully in %d ms (%d row(s) affected)",
                        executionTimeMs, effectiveCount);

                if (metricsService != null) {
                    metricsService.recordExecution(true, extractTable(sql), extractOperation(sql), null, null);
                }

                return SqlQueryResponse.builder()
                        .columns(columns)
                        .rows(rows)
                        .rowCount(effectiveCount)
                        .executionTimeMs(executionTimeMs)
                        .message(summary)
                        .hasResultSet(false)
                        .build();
            }

        } catch (SQLException e) {
            log.warn("SQL execution error on container {}: {}", container.getId(), e.getMessage());
            if (metricsService != null) {
                metricsService.recordExecution(false, extractTable(sql), extractOperation(sql), e.getSQLState(), e.getMessage());
            }
            throw new SqlQueryException(e.getMessage(), e);
        }
    }

    private Container resolveContainer(Sandbox sandbox, String requestedContainerId) {
        if (requestedContainerId != null && !requestedContainerId.isBlank()) {
            return containerRepository.findById(requestedContainerId)
                    .filter(c -> c.getSandbox() != null && c.getSandbox().getId().equals(sandbox.getId()))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Container " + requestedContainerId + " not found in sandbox " + sandbox.getId()));
        }

        List<Container> runningContainers = containerRepository.findBySandboxIdAndStatus(
                sandbox.getId(), ContainerStatus.RUNNING);

        if (runningContainers.isEmpty()) {
            throw new SandboxOperationException(
                    "Sandbox " + sandbox.getId() + " has no running containers. Please start the sandbox first.");
        }

        return runningContainers.get(0);
    }

    private Object formatValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof byte[]) {
            return "<binary data>";
        }
        return val.toString();
    }

    private String extractOperation(String sql) {
        if (sql == null || sql.isBlank()) {
            return "QUERY";
        }
        String trimmed = sql.trim().toUpperCase();
        if (trimmed.startsWith("SELECT")) return "SELECT";
        if (trimmed.startsWith("INSERT")) return "INSERT";
        if (trimmed.startsWith("UPDATE")) return "UPDATE";
        if (trimmed.startsWith("DELETE")) return "DELETE";
        if (trimmed.startsWith("CREATE")) return "CREATE";
        if (trimmed.startsWith("DROP")) return "DROP";
        if (trimmed.startsWith("ALTER")) return "ALTER";
        if (trimmed.startsWith("TRUNCATE")) return "TRUNCATE";
        return "QUERY";
    }

    private String extractTable(String sql) {
        if (sql == null || sql.isBlank()) {
            return "unknown";
        }
        try {
            String[] tokens = sql.trim().split("\\s+");
            for (int i = 0; i < tokens.length - 1; i++) {
                String t = tokens[i].toUpperCase();
                if ("FROM".equals(t) || "INTO".equals(t) || "UPDATE".equals(t) || "TABLE".equals(t)) {
                    String candidate = tokens[i + 1].replaceAll("[,;\"'`\\[\\]]", "");
                    if (!candidate.isBlank() && !candidate.equalsIgnoreCase("SELECT")) {
                        return candidate;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }
}
