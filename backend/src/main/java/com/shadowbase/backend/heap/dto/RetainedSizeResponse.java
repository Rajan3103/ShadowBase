package com.shadowbase.backend.heap.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed retained size metrics for a specific heap object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetainedSizeResponse {

    private long objectId;
    private String className;
    private long shallowSizeBytes;
    private long retainedSizeBytes;
    private double percentageOfTotalHeap;
    private int dominatedObjectCount;
    private long immediateDominatorId;
}
