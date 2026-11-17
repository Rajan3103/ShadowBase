package com.shadowbase.backend.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) containing preflight validation results for a requested data seeding job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSeedValidationResponse {

    /**
     * Whether all preflight checks passed and the job is safe to execute.
     */
    private boolean valid;

    /**
     * Fatal validation errors that prevent seeding (e.g. schema mismatch, non-empty tables, cycles).
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * Non-fatal informational warnings (e.g. tables with 0 rows, unindexed foreign keys).
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * List of tables that will be transferred.
     */
    @Builder.Default
    private List<String> tablesToTransfer = new ArrayList<>();

    /**
     * Ordered list of tables sorted by foreign key dependencies (parents before children).
     */
    @Builder.Default
    private List<String> dependencyOrder = new ArrayList<>();
}
