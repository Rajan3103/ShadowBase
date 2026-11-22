package com.shadowbase.backend.controller;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.shadowbase.backend.dto.MetricsSummaryResponse;
import com.shadowbase.backend.dto.RecordMetricRequest;
import com.shadowbase.backend.dto.ReplayErrorDto;
import com.shadowbase.backend.service.MetricsService;

@ExtendWith(MockitoExtension.class)
class MetricsControllerTest {

    @Mock
    private MetricsService metricsService;

    private MetricsController metricsController;

    @BeforeEach
    void setUp() {
        metricsController = new MetricsController(metricsService);
    }

    @Test
    void testGetMetrics() {
        MetricsSummaryResponse mockResponse = MetricsSummaryResponse.builder()
                .replayed(100)
                .failed(5)
                .errorRate(5.0)
                .lastUpdatedTs(System.currentTimeMillis())
                .build();

        when(metricsService.getMetrics()).thenReturn(mockResponse);

        ResponseEntity<MetricsSummaryResponse> response = metricsController.getMetrics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100, response.getBody().getReplayed());
        assertEquals(5, response.getBody().getFailed());
        assertEquals(5.0, response.getBody().getErrorRate());
    }

    @Test
    void testGetRecentErrors() {
        ReplayErrorDto error = ReplayErrorDto.builder()
                .id("err-1")
                .ts(System.currentTimeMillis())
                .table("orders")
                .operation("INSERT")
                .sqlState("42P01")
                .message("Table does not exist")
                .build();

        when(metricsService.getRecentErrors(anyInt())).thenReturn(Collections.singletonList(error));

        ResponseEntity<List<ReplayErrorDto>> response = metricsController.getRecentErrors(10);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("orders", response.getBody().get(0).getTable());
    }

    @Test
    void testRecordMetric() {
        RecordMetricRequest request = RecordMetricRequest.builder()
                .success(false)
                .table("customers")
                .operation("UPDATE")
                .sqlState("23505")
                .message("Unique constraint violated")
                .build();

        MetricsSummaryResponse summary = MetricsSummaryResponse.builder()
                .replayed(1)
                .failed(1)
                .errorRate(100.0)
                .build();

        doNothing().when(metricsService).recordExecution(anyBoolean(), anyString(), anyString(), anyString(), anyString());
        when(metricsService.getMetrics()).thenReturn(summary);

        ResponseEntity<MetricsSummaryResponse> response = metricsController.recordMetric(request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(metricsService).recordExecution(false, "customers", "UPDATE", "23505", "Unique constraint violated");
    }

    @Test
    void testResetMetrics() {
        MetricsSummaryResponse summary = MetricsSummaryResponse.builder()
                .replayed(0)
                .failed(0)
                .errorRate(0.0)
                .build();

        doNothing().when(metricsService).resetMetrics();
        when(metricsService.getMetrics()).thenReturn(summary);

        ResponseEntity<MetricsSummaryResponse> response = metricsController.resetMetrics();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(metricsService).resetMetrics();
    }
}
