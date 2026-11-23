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
}
