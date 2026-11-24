package com.shadowbase.backend.controller;

import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shadowbase.backend.dto.MigrationAnalysisRequest;
import com.shadowbase.backend.dto.MigrationAnalysisResponse;
import com.shadowbase.backend.dto.RecordColumnUsageRequest;
import com.shadowbase.backend.service.ColumnUsageTracker;
import com.shadowbase.backend.service.MigrationAnalysisService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller providing automated static analysis for SQL migration scripts.
 * Evaluates table drops, column drops/renames, type alterations, and constraint violations
 * before changes are replayed against shadow database environments.
 * <p>
 * Base URIs: {@code /api/analysis}, {@code /api/analysis/migration-risk}
 */
@Slf4j
@RestController
@RequestMapping
public class MigrationAnalysisController {

    private final MigrationAnalysisService analysisService;
    private final ColumnUsageTracker columnUsageTracker;

    public MigrationAnalysisController(MigrationAnalysisService analysisService) {
        this(analysisService, null);
    }

    @Autowired
    public MigrationAnalysisController(
            MigrationAnalysisService analysisService,
            @Autowired(required = false) ColumnUsageTracker columnUsageTracker) {
        this.analysisService = analysisService;
        this.columnUsageTracker = columnUsageTracker;
    }

    /**
     * Analyzes a proposed SQL migration script for breaking changes and operational risks.
     *
     * @param request payload containing raw SQL script
     * @return 200 OK with categorized risk counts and specific findings
     */
    @PostMapping({"/api/analysis", "/api/analysis/migration-risk"})
    public ResponseEntity<MigrationAnalysisResponse> analyzeMigration(
            @RequestBody MigrationAnalysisRequest request) {

        log.info("REST request to analyze SQL migration risk (script length: {} chars)",
                request != null && request.getSql() != null ? request.getSql().length() : 0);

        MigrationAnalysisResponse response = analysisService.analyzeMigration(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves recorded query usage telemetry for columns in a given table.
     *
     * @param table target table name
     * @return 200 OK with map of column -> query count
     */
    @GetMapping("/api/analysis/column-usage")
    public ResponseEntity<Map<String, Long>> getColumnUsage(
            @RequestParam("table") String table) {
        if (columnUsageTracker == null) {
            return ResponseEntity.ok(Collections.emptyMap());
        }
        return ResponseEntity.ok(columnUsageTracker.getTableColumnUsage(table));
    }

    /**
     * Records or seeds query access telemetry for a column.
     *
     * @param request payload specifying table, column, and access count
     * @return 200 OK
     */
    @PostMapping("/api/analysis/column-usage")
    public ResponseEntity<Void> recordColumnUsage(
            @RequestBody RecordColumnUsageRequest request) {
        if (columnUsageTracker != null && request != null) {
            long count = (request.getCount() != null && request.getCount() > 0) ? request.getCount() : 1L;
            columnUsageTracker.recordColumnAccess(request.getTable(), request.getColumn(), count);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * Resets recorded column access metrics.
     *
     * @return 200 OK
     */
    @PostMapping("/api/analysis/column-usage/reset")
    public ResponseEntity<Void> resetColumnUsage() {
        if (columnUsageTracker != null) {
            columnUsageTracker.reset();
        }
        return ResponseEntity.ok().build();
    }
}
