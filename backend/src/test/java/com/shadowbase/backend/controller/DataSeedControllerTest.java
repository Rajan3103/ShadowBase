package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.shadowbase.backend.Entity.SeedJobStatus;
import com.shadowbase.backend.dto.DataSeedRequest;
import com.shadowbase.backend.dto.DataSeedValidationResponse;
import com.shadowbase.backend.dto.SeedJobResponse;
import com.shadowbase.backend.service.seeding.DataSeedingService;

/**
 * Fast unit tests for {@link DataSeedController}.
 * Verifies endpoint wiring, HTTP status mappings (202 Accepted, 200 OK), and response encapsulation.
 */
@ExtendWith(MockitoExtension.class)
class DataSeedControllerTest {

    @Mock
    private DataSeedingService dataSeedingService;

    private DataSeedController controller;
    private UUID sandboxId;

    @BeforeEach
    void setUp() {
        controller = new DataSeedController(dataSeedingService);
        sandboxId = UUID.randomUUID();
    }

    @Test
    void testStartSeedJob_Returns202Accepted() {
        DataSeedRequest request = new DataSeedRequest();
        UUID jobId = UUID.randomUUID();
        SeedJobResponse mockResponse = SeedJobResponse.builder()
                .id(jobId)
                .sandboxId(sandboxId)
                .status(SeedJobStatus.QUEUED)
                .build();

        when(dataSeedingService.startSeedJob(eq(sandboxId), any())).thenReturn(mockResponse);

        ResponseEntity<SeedJobResponse> response = controller.startSeedJob(sandboxId, request);

        assertNotNull(response);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(jobId, response.getBody().getId());
        assertEquals(SeedJobStatus.QUEUED, response.getBody().getStatus());
        verify(dataSeedingService).startSeedJob(eq(sandboxId), eq(request));
    }

    @Test
    void testValidatePreflight_Returns200Ok() {
        DataSeedValidationResponse mockResponse = DataSeedValidationResponse.builder()
                .valid(true)
                .tablesToTransfer(List.of("users"))
                .build();

        when(dataSeedingService.validatePreflight(eq(sandboxId), any())).thenReturn(mockResponse);

        ResponseEntity<DataSeedValidationResponse> response = controller.validatePreflight(sandboxId, new DataSeedRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().isValid());
    }

    @Test
    void testGetJob_Returns200Ok() {
        UUID jobId = UUID.randomUUID();
        SeedJobResponse mockResponse = SeedJobResponse.builder()
                .id(jobId)
                .sandboxId(sandboxId)
                .status(SeedJobStatus.RUNNING)
                .build();

        when(dataSeedingService.getJob(sandboxId, jobId)).thenReturn(mockResponse);

        ResponseEntity<SeedJobResponse> response = controller.getJob(sandboxId, jobId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(jobId, response.getBody().getId());
    }

    @Test
    void testCancelJob_Returns200Ok() {
        UUID jobId = UUID.randomUUID();
        SeedJobResponse mockResponse = SeedJobResponse.builder()
                .id(jobId)
                .sandboxId(sandboxId)
                .status(SeedJobStatus.CANCELLING)
                .build();

        when(dataSeedingService.cancelJob(sandboxId, jobId)).thenReturn(mockResponse);

        ResponseEntity<SeedJobResponse> response = controller.cancelJob(sandboxId, jobId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(SeedJobStatus.CANCELLING, response.getBody().getStatus());
    }
}
