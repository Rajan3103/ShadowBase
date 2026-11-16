package com.shadowbase.backend.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.Entity.ConsistencyMode;
import com.shadowbase.backend.Entity.SeedingMode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing a request to start or validate a data seeding job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSeedRequest {

    /**
     * Mode of table selection: FULL_DATABASE or SELECTED_TABLES.
     * Defaults to FULL_DATABASE.
     */
    @Builder.Default
    private SeedingMode mode = SeedingMode.FULL_DATABASE;

    /**
     * List of specific table names to copy when mode is SELECTED_TABLES.
     */
    @Builder.Default
    private List<String> tables = new ArrayList<>();

    /**
     * Source read consistency mode: SNAPSHOT (repeatable read) or STANDARD.
     * Defaults to SNAPSHOT.
     */
    @Builder.Default
    private ConsistencyMode consistencyMode = ConsistencyMode.SNAPSHOT;

    /**
     * Optional target container ID within the sandbox.
     * If omitted and the sandbox has exactly one running container, it is resolved automatically.
     */
    private String containerId;

    /**
     * Optional user ID of the requester for authorization validation.
     */
    private UUID userId;
}
