package com.shadowbase.backend.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shadowbase.backend.dto.MetricsSummaryResponse;
import com.shadowbase.backend.dto.ReplayErrorDto;

class MetricsServiceTest {

    private MetricsServiceImpl metricsService;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsServiceImpl();
    }

    @Test
    void testInitialMetrics() {
        MetricsSummaryResponse summary = metricsService.getMetrics();
        assertEquals(0, summary.getReplayed());
        assertEquals(0, summary.getFailed());
        assertEquals(0.0, summary.getErrorRate());
        assertTrue(metricsService.getRecentErrors(10).isEmpty());
    }

    @Test
    void testRecordSuccessAndFailure() {
        metricsService.recordExecution(true, "users", "SELECT", null, null);
        metricsService.recordExecution(true, "users", "INSERT", null, null);
        metricsService.recordExecution(false, "orders", "INSERT", "42P01", "relation orders does not exist");

        MetricsSummaryResponse summary = metricsService.getMetrics();
        assertEquals(3, summary.getReplayed());
        assertEquals(1, summary.getFailed());
        assertEquals(33.3, summary.getErrorRate());

        List<ReplayErrorDto> errors = metricsService.getRecentErrors(10);
        assertEquals(1, errors.size());
        ReplayErrorDto err = errors.get(0);
        assertEquals("orders", err.getTable());
        assertEquals("INSERT", err.getOperation());
        assertEquals("42P01", err.getSqlState());
        assertEquals("relation orders does not exist", err.getMessage());
        assertNotNull(err.getId());
        assertTrue(err.getTs() > 0);
    }

    @Test
    void testResetMetrics() {
        metricsService.recordExecution(false, "users", "DELETE", "23503", "FK violation");
        assertEquals(1, metricsService.getMetrics().getFailed());
        assertFalse(metricsService.getRecentErrors(10).isEmpty());

        metricsService.resetMetrics();
        assertEquals(0, metricsService.getMetrics().getReplayed());
        assertEquals(0, metricsService.getMetrics().getFailed());
        assertTrue(metricsService.getRecentErrors(10).isEmpty());
    }
}
