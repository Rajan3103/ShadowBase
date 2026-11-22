package com.shadowbase.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shadowbase.backend.dto.MigrationAnalysisRequest;
import com.shadowbase.backend.dto.MigrationAnalysisResponse;
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

    public MigrationAnalysisController(MigrationAnalysisService analysisService) {
        this.analysisService = analysisService;
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
}
