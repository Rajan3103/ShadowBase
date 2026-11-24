package com.shadowbase.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a dropped column target extracted from an SQL AST statement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroppedColumnTarget {

    /**
     * Name of the table owning the column.
     */
    private String table;

    /**
     * Name of the dropped column.
     */
    private String column;

    /**
     * Line number where the statement was encountered in the script.
     */
    private int line;

    /**
     * The raw SQL statement string.
     */
    private String statement;
}
