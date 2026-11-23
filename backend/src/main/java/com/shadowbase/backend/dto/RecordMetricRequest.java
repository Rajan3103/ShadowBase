package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload to record a query execution or replay result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordMetricRequest {

    private boolean success;
    private String table;
    private String operation;
    private String sqlState;
    private String message;
}
