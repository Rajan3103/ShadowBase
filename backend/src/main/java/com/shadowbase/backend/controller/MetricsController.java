package com.shadowbase.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shadowbase.backend.dto.MetricsSummaryResponse;
import com.shadowbase.backend.dto.RecordMetricRequest;
import com.shadowbase.backend.dto.ReplayErrorDto;
import com.shadowbase.backend.service.MetricsService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller exposing live shadow-traffic metrics and recent SQL execution errors
 * for the ShadowBase dashboard and live error console.
 * <p>
 * Base URI: {@code /api/metrics}
 */
@Slf4j
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * Retrieves current shadow-traffic metrics ({ replayed, failed, errorRate }).
     *
     * @return 200 OK with MetricsSummaryResponse
     */
    @GetMapping
    public ResponseEntity<MetricsSummaryResponse> getMetrics() {
        return ResponseEntity.ok(metricsService.getMetrics());
    }

    /**
     * Retrieves recent SQL execution and replay errors for the terminal error console.
     *
     * @param limit maximum errors to retrieve (default 50)
     * @return 200 OK with list of ReplayErrorDto
     */
    @GetMapping("/errors")
    public ResponseEntity<List<ReplayErrorDto>> getRecentErrors(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        return ResponseEntity.ok(metricsService.getRecentErrors(limit));
    }

    /**
     * Records an execution result (success or failure) to update live counters.
     *
     * @param request metric details
     * @return 201 CREATED with updated MetricsSummaryResponse
     */
    @PostMapping("/record")
    public ResponseEntity<MetricsSummaryResponse> recordMetric(@RequestBody RecordMetricRequest request) {
        if (request != null) {
            metricsService.recordExecution(
                    request.isSuccess(),
                    request.getTable(),
                    request.getOperation(),
                    request.getSqlState(),
                    request.getMessage());
        }
        return new ResponseEntity<>(metricsService.getMetrics(), HttpStatus.CREATED);
    }

    /**
     * Resets metrics counters and clears the error console buffer.
     *
     * @return 200 OK with reset MetricsSummaryResponse
     */
    @PostMapping("/reset")
    public ResponseEntity<MetricsSummaryResponse> resetMetrics() {
        metricsService.resetMetrics();
        return ResponseEntity.ok(metricsService.getMetrics());
    }
}
