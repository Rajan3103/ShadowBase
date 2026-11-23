package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload containing a SQL migration script to analyze for operational risks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationAnalysisRequest {

    private String sql;
}
