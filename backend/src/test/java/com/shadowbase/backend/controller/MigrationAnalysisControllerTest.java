package com.shadowbase.backend.controller;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.shadowbase.backend.dto.MigrationAnalysisRequest;
import com.shadowbase.backend.dto.MigrationAnalysisResponse;
import com.shadowbase.backend.dto.MigrationFindingDto;
import com.shadowbase.backend.service.MigrationAnalysisService;

@ExtendWith(MockitoExtension.class)
class MigrationAnalysisControllerTest {

    @Mock
    private MigrationAnalysisService analysisService;

    private MigrationAnalysisController controller;

    @BeforeEach
    void setUp() {
        controller = new MigrationAnalysisController(analysisService);
    }

    @Test
    void testAnalyzeMigrationEndpoint() {
        MigrationAnalysisResponse mockResponse = MigrationAnalysisResponse.builder()
                .totalRisks(1)
                .highRisks(1)
                .hasBreakingRisks(true)
                .findings(Collections.singletonList(
                        MigrationFindingDto.builder()
                                .level("high")
                                .rule("DROP_TABLE")
                                .message("Dropping table will break queries")
                                .line(1)
                                .table("orders")
                                .build()))
                .build();

        when(analysisService.analyzeMigration(any(MigrationAnalysisRequest.class))).thenReturn(mockResponse);

        ResponseEntity<MigrationAnalysisResponse> response = controller.analyzeMigration(
                new MigrationAnalysisRequest("DROP TABLE orders;"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalRisks());
        assertEquals(1, response.getBody().getHighRisks());
        assertEquals("DROP_TABLE", response.getBody().getFindings().get(0).getRule());
    }

    @Test
    void testColumnUsageEndpointsWithTracker() {
        com.shadowbase.backend.service.ColumnUsageTracker tracker =
                new com.shadowbase.backend.service.ColumnUsageTrackerImpl(10);
        MigrationAnalysisController controllerWithTracker =
                new MigrationAnalysisController(analysisService, tracker);

        // 1. Record column usage
        com.shadowbase.backend.dto.RecordColumnUsageRequest req =
                new com.shadowbase.backend.dto.RecordColumnUsageRequest("users", "email", 12L);
        ResponseEntity<Void> recordRes = controllerWithTracker.recordColumnUsage(req);
        assertEquals(HttpStatus.OK, recordRes.getStatusCode());

        // 2. Query column usage
        ResponseEntity<java.util.Map<String, Long>> getRes = controllerWithTracker.getColumnUsage("users");
        assertEquals(HttpStatus.OK, getRes.getStatusCode());
        assertNotNull(getRes.getBody());
        assertEquals(12L, getRes.getBody().get("email"));

        // 3. Reset column usage
        ResponseEntity<Void> resetRes = controllerWithTracker.resetColumnUsage();
        assertEquals(HttpStatus.OK, resetRes.getStatusCode());
        assertEquals(0L, tracker.getQueryCount("users", "email"));
    }
}
