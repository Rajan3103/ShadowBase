package com.shadowbase.backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shadowbase.backend.Entity.SeedTableStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing table-level progress within a seeding job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeedTableProgressResponse {

    private UUID id;
    private String tableName;
    private SeedTableStatus status;
    private Long rowsRead;
    private Long rowsInserted;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
}
