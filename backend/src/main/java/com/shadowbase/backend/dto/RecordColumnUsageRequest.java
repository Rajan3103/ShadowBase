package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload to record or seed column query usage telemetry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordColumnUsageRequest {

    /**
     * Database table name.
     */
    private String table;

    /**
     * Column name.
     */
    private String column;

    /**
     * Number of queries to increment (defaults to 1 if omitted).
     */
    private Long count;
}
