package com.shadowbase.backend.Entity;

/**
 * Lifecycle status of an asynchronous data seeding job.
 * <p>
 * Tracks execution state independently from {@link Sandbox.SandboxStatus} and {@link SchemaStatus}.
 */
public enum SeedJobStatus {
    /** Job is registered and waiting to be claimed by the background worker. */
    QUEUED,

    /** Preflight schema inspection, compatibility checks, and dependency planning in progress. */
    VALIDATING,

    /** Data is actively being streamed from source to target sandbox container. */
    RUNNING,

    /** User requested cancellation; worker is safely terminating at the nearest batch boundary. */
    CANCELLING,

    /** Job was cancelled before or during execution. */
    CANCELLED,

    /** All planned tables and records were transferred successfully. */
    SUCCESS,

    /** Transfer encountered an unrecoverable error and stopped. */
    FAILED,

    /** Some tables completed, but a subsequent table failed. */
    PARTIAL_FAILURE,

    /** Worker was unexpectedly stopped or restarted while the job was RUNNING or CANCELLING. */
    INTERRUPTED
}
