package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary metrics for shadow database traffic and query execution.
 * Matched to the frontend dashboard contract: { replayed, failed, errorRate }.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsSummaryResponse {

    private long replayed;
    private long failed;
    private double errorRate;
    private long lastUpdatedTs;
}
