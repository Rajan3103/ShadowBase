package com.shadowbase.backend.service;

import java.util.List;

import com.shadowbase.backend.dto.MetricsSummaryResponse;
import com.shadowbase.backend.dto.ReplayErrorDto;

/**
 * Service managing real-time query replay metrics, failure rates, and error logs
 * for the live dashboard and error console.
 */
public interface MetricsService {

    /**
     * Retrieves current traffic metrics (total replayed, failed, and percentage error rate).
     */
    MetricsSummaryResponse getMetrics();

    /**
     * Retrieves recent execution errors up to the specified limit.
     */
    List<ReplayErrorDto> getRecentErrors(int limit);

    /**
     * Records an execution result (success or failure) and buffers any error details.
     */
    void recordExecution(boolean success, String table, String operation, String sqlState, String message);

    /**
     * Resets counters and clears the error buffer.
     */
    void resetMetrics();
}
