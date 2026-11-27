package com.shadowbase.backend.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated report of operational risks and breaking changes in a migration script.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationAnalysisResponse {

    private int totalRisks;
    private int highRisks;
    private int mediumRisks;
    private int lowRisks;
    private boolean hasBreakingRisks;

    @Builder.Default
    private int heavilyQueriedCount = 0;

    @Builder.Default
    private List<MigrationFindingDto> findings = new ArrayList<>();
}
