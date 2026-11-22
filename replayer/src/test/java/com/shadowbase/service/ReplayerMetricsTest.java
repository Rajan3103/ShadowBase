package com.shadowbase.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplayMetricsTest {

    @Test
    void shouldRecordSuccessfulReplay() {

        ReplayMetrics metrics = new ReplayMetrics();

        metrics.recordSuccess();

        assertEquals(1, metrics.getTotalReplays());
        assertEquals(1, metrics.getSuccessfulReplays());
        assertEquals(0, metrics.getFailedReplays());
        assertEquals(0.0, metrics.getErrorRate());
    }

    @Test
    void shouldRecordFailedReplay() {

        ReplayMetrics metrics = new ReplayMetrics();

        metrics.recordFailure();

        assertEquals(1, metrics.getTotalReplays());
        assertEquals(0, metrics.getSuccessfulReplays());
        assertEquals(1, metrics.getFailedReplays());
        assertEquals(1.0, metrics.getErrorRate());
    }

    @Test
    void shouldCalculateErrorRate() {

        ReplayMetrics metrics = new ReplayMetrics();

        metrics.recordSuccess();
        metrics.recordSuccess();
        metrics.recordFailure();

        assertEquals(3, metrics.getTotalReplays());
        assertEquals(2, metrics.getSuccessfulReplays());
        assertEquals(1, metrics.getFailedReplays());

        assertEquals(
                1.0 / 3.0,
                metrics.getErrorRate(),
                0.0001
        );
    }
}