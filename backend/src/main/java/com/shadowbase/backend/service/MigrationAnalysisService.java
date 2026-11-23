package com.shadowbase.backend.service;

import com.shadowbase.backend.dto.MigrationAnalysisRequest;
import com.shadowbase.backend.dto.MigrationAnalysisResponse;

/**
 * Service that analyzes SQL migration scripts for breaking schema changes and operational risks
 * before applying them to a shadow or production database.
 */
public interface MigrationAnalysisService {

    /**
     * Parses and analyzes a SQL script, evaluating potential impact on live queries,
     * table locking, and schema compatibility.
     *
     * @param request payload containing the SQL script
     * @return MigrationAnalysisResponse detailing risk counts, breaking flags, and specific findings
     */
    MigrationAnalysisResponse analyzeMigration(MigrationAnalysisRequest request);
}
