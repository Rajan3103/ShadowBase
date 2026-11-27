package com.shadowbase.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import com.shadowbase.backend.dto.MetricsSummaryResponse;
import com.shadowbase.backend.dto.ReplayErrorDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe implementation of {@link MetricsService}.
 * Maintains live query and replay traffic counters and a bounded circular error buffer.
 */
@Slf4j
@Service
public class MetricsServiceImpl implements MetricsService {

    private static final int MAX_BUFFERED_ERRORS = 500;

    private final AtomicLong totalReplayed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final ConcurrentLinkedDeque<ReplayErrorDto> errorBuffer = new ConcurrentLinkedDeque<>();
    private final ColumnUsageTracker columnUsageTracker;

    public MetricsServiceImpl() {
        this(null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MetricsServiceImpl(
            @org.springframework.beans.factory.annotation.Autowired(required = false) ColumnUsageTracker columnUsageTracker) {
        this.columnUsageTracker = columnUsageTracker;
    }

    @Override
    public MetricsSummaryResponse getMetrics() {
        long replayed = totalReplayed.get();
        long failed = totalFailed.get();
        double errorRate = 0.0;

        if (replayed > 0) {
            double rate = ((double) failed / (double) replayed) * 100.0;
            errorRate = Math.round(rate * 10.0) / 10.0;
        }

        return MetricsSummaryResponse.builder()
                .replayed(replayed)
                .failed(failed)
                .errorRate(errorRate)
                .lastUpdatedTs(System.currentTimeMillis())
                .build();
    }

    @Override
    public List<ReplayErrorDto> getRecentErrors(int limit) {
        int effectiveLimit = (limit <= 0) ? 50 : Math.min(limit, MAX_BUFFERED_ERRORS);
        List<ReplayErrorDto> result = new ArrayList<>(effectiveLimit);

        for (ReplayErrorDto error : errorBuffer) {
            result.add(error);
            if (result.size() >= effectiveLimit) {
                break;
            }
        }

        return result;
    }

    @Override
    public void recordExecution(boolean success, String table, String operation, String sqlState, String message) {
        totalReplayed.incrementAndGet();

        if (!success) {
            totalFailed.incrementAndGet();

            ReplayErrorDto errorDto = ReplayErrorDto.builder()
                    .id(UUID.randomUUID().toString())
                    .ts(System.currentTimeMillis())
                    .table((table != null && !table.isBlank()) ? table : "unknown")
                    .operation((operation != null && !operation.isBlank()) ? operation.toUpperCase() : "QUERY")
                    .sqlState((sqlState != null && !sqlState.isBlank()) ? sqlState : "42000")
                    .message((message != null && !message.isBlank()) ? message : "Execution failed")
                    .build();

            errorBuffer.addFirst(errorDto);

            while (errorBuffer.size() > MAX_BUFFERED_ERRORS) {
                errorBuffer.pollLast();
            }

            log.debug("Recorded query failure: table={}, op={}, message={}", table, operation, message);
        }
    }

    @Override
    public void recordExecution(boolean success, String table, String operation, String sqlState, String message, List<String> columns) {
        recordExecution(success, table, operation, sqlState, message);
        if (columnUsageTracker != null && table != null && !table.isBlank() && columns != null) {
            for (String col : columns) {
                if (col != null && !col.isBlank()) {
                    columnUsageTracker.recordColumnAccess(table, col);
                }
            }
        }
    }

    @Override
    public void resetMetrics() {
        totalReplayed.set(0);
        totalFailed.set(0);
        errorBuffer.clear();
        log.info("Reset replay metrics and error console buffer.");
    }
}
