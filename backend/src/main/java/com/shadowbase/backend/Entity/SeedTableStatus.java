package com.shadowbase.backend.Entity;

/**
 * Execution status of data transfer for an individual table within a {@link SeedJob}.
 */
public enum SeedTableStatus {
    /** Table transfer has not yet started. */
    PENDING,

    /** Rows are actively being streamed and inserted in batches. */
    RUNNING,

    /** All rows for this table were transferred and committed. */
    SUCCESS,

    /** An error occurred during row transfer for this table. */
    FAILED,

    /** Skipped due to an earlier failure in the dependency chain or cancellation. */
    SKIPPED
}
