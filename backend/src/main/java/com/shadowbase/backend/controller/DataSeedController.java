package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shadowbase.backend.dto.DataSeedRequest;
import com.shadowbase.backend.dto.DataSeedValidationResponse;
import com.shadowbase.backend.dto.SeedJobResponse;
import com.shadowbase.backend.service.seeding.DataSeedingService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller exposing management endpoints for asynchronous data seeding jobs
 * in ShadowBase sandbox environments.
 * <p>
 * Base URI: {@code /api/sandboxes/{sandboxId}/data-seeds}
 */
@Slf4j
@RestController
@RequestMapping("/api/sandboxes/{sandboxId}/data-seeds")
public class DataSeedController {

    private final DataSeedingService dataSeedingService;

    public DataSeedController(DataSeedingService dataSeedingService) {
        this.dataSeedingService = dataSeedingService;
    }

    /**
     * Initiates a new asynchronous data seeding job.
     *
     * @param sandboxId UUID of the sandbox
     * @param request   payload specifying mode, tables, consistency, and optional container/user IDs
     * @return 202 ACCEPTED with the initial SeedJobResponse
     */
    @PostMapping
    public ResponseEntity<SeedJobResponse> startSeedJob(
            @PathVariable("sandboxId") UUID sandboxId,
            @RequestBody(required = false) DataSeedRequest request) {

        log.info("REST request to start data seeding for sandbox: {}", sandboxId);
        SeedJobResponse response = dataSeedingService.startSeedJob(sandboxId, request);
        return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
    }

    /**
     * Executes preflight validation for a prospective seeding job without transferring data.
     *
     * @param sandboxId UUID of the sandbox
     * @param request   payload containing proposed seeding options
     * @return 200 OK with DataSeedValidationResponse
     */
    @PostMapping("/validate")
    public ResponseEntity<DataSeedValidationResponse> validatePreflight(
            @PathVariable("sandboxId") UUID sandboxId,
            @RequestBody(required = false) DataSeedRequest request) {

        log.info("REST request to validate data seeding preflight for sandbox: {}", sandboxId);
        DataSeedValidationResponse response = dataSeedingService.validatePreflight(sandboxId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the seeding history for a sandbox.
     *
     * @param sandboxId UUID of the sandbox
     * @return 200 OK with list of previous SeedJobResponse objects
     */
    @GetMapping
    public ResponseEntity<List<SeedJobResponse>> listJobs(
            @PathVariable("sandboxId") UUID sandboxId) {

        return ResponseEntity.ok(dataSeedingService.listJobs(sandboxId));
    }

    /**
     * Retrieves detailed status and table-level progress for a specific seeding job.
     *
     * @param sandboxId UUID of the sandbox
     * @param jobId     UUID of the seeding job
     * @return 200 OK with SeedJobResponse
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<SeedJobResponse> getJob(
            @PathVariable("sandboxId") UUID sandboxId,
            @PathVariable("jobId") UUID jobId) {

        return ResponseEntity.ok(dataSeedingService.getJob(sandboxId, jobId));
    }

    /**
     * Requests graceful cancellation of an active seeding job.
     *
     * @param sandboxId UUID of the sandbox
     * @param jobId     UUID of the seeding job
     * @return 200 OK with updated SeedJobResponse
     */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<SeedJobResponse> cancelJob(
            @PathVariable("sandboxId") UUID sandboxId,
            @PathVariable("jobId") UUID jobId) {

        log.info("REST request to cancel seed job {} in sandbox {}", jobId, sandboxId);
        return ResponseEntity.ok(dataSeedingService.cancelJob(sandboxId, jobId));
    }
}
