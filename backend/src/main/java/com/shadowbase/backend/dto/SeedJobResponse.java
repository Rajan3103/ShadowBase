package com.shadowbase.backend.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.Entity.ConsistencyMode;
import com.shadowbase.backend.Entity.SeedJobStatus;
import com.shadowbase.backend.Entity.SeedingMode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) returning seed job status, summary counters, and table progress.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeedJobResponse {

    private UUID id;
    private UUID sandboxId;
    private String containerId;
    private UUID sourceDataSourceId;
    private SeedJobStatus status;
    private SeedingMode mode;
    private ConsistencyMode consistencyMode;
    private Integer totalTables;
    private Integer completedTables;
    private Long totalRowsRead;
    private Long totalRowsInserted;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private String createdBy;

    @Builder.Default
    private List<SeedTableProgressResponse> tables = new ArrayList<>();
}
