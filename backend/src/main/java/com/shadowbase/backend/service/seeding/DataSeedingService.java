package com.shadowbase.backend.service.seeding;

import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.dto.DataSeedRequest;
import com.shadowbase.backend.dto.DataSeedValidationResponse;
import com.shadowbase.backend.dto.SeedJobResponse;

/**
 * Service contract for coordinating data seeding workflows from external source DataSources
 * into isolated database containers inside ShadowBase sandboxes.
 */
public interface DataSeedingService {

    /**
     * Initiates an asynchronous data seeding job after performing preflight validations.
     *
     * @param sandboxId UUID of the target sandbox
     * @param request   configuration parameters for the seeding job
     * @return initial SeedJobResponse in QUEUED status
     */
    SeedJobResponse startSeedJob(UUID sandboxId, DataSeedRequest request);

    /**
     * Executes preflight validation without starting an actual data transfer.
     * Validates database reachability, schema compatibility, empty targets, and dependency ordering.
     *
     * @param sandboxId UUID of the sandbox
     * @param request   seeding request options
     * @return validation response containing status, errors, and planned table order
     */
    DataSeedValidationResponse validatePreflight(UUID sandboxId, DataSeedRequest request);

    /**
     * Retrieves detailed status and per-table progress for a specific seeding job.
     *
     * @param sandboxId UUID of the sandbox
     * @param jobId     UUID of the seeding job
     * @return SeedJobResponse with table-level progress
     */
    SeedJobResponse getJob(UUID sandboxId, UUID jobId);

    /**
     * Lists previous seeding attempts and jobs associated with a sandbox.
     *
     * @param sandboxId UUID of the sandbox
     * @return list of SeedJobResponse objects ordered newest to oldest
     */
    List<SeedJobResponse> listJobs(UUID sandboxId);

    /**
     * Requests graceful cancellation of an active seeding job at the nearest batch boundary.
     *
     * @param sandboxId UUID of the sandbox
     * @param jobId     UUID of the seeding job
     * @return updated SeedJobResponse in CANCELLING or CANCELLED status
     */
    SeedJobResponse cancelJob(UUID sandboxId, UUID jobId);
}
