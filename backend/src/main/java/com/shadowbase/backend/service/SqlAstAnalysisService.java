package com.shadowbase.backend.service;

import java.util.List;

import com.shadowbase.backend.dto.DroppedColumnTarget;
import com.shadowbase.backend.dto.MigrationFindingDto;

/**
 * Service for AST-based SQL analysis using JSqlParser.
 * Detects structural schema modifications such as dropped columns
 * and cross-references them against live query traffic to protect against breaking migrations.
 */
public interface SqlAstAnalysisService {

    /**
     * Extracts all dropped columns identified in the migration SQL AST.
     *
     * @param sql raw SQL script containing one or more statements
     * @return list of dropped column targets
     */
    List<DroppedColumnTarget> extractDroppedColumns(String sql);

    /**
     * Inspects SQL script and detects whether any dropped columns are currently
     * heavily queried in production/replay traffic.
     *
     * @param sql raw SQL script
     * @param threshold minimum query count to be considered heavily queried
     * @return list of AST-driven risk findings (e.g., DROPPED_HEAVILY_QUERIED_COLUMN)
     */
    List<MigrationFindingDto> analyzeColumnDropRisks(String sql, int threshold);
}
