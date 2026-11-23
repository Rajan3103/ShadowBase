package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single finding from analyzing a SQL migration script for operational risks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationFindingDto {

    private String level;
    private String rule;
    private String message;
    private int line;
    private String statement;
    private String table;
}
