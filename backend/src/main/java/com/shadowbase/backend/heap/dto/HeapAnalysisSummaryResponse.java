package com.shadowbase.backend.heap.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * High-level summary of a parsed and analyzed heap dump, providing key memory metrics,
 * class histogram, and top memory-dominating objects.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeapAnalysisSummaryResponse {

    private String snapshotId;
    private String headerVersion;
    private int idSize;
    private long dumpTimestamp;
    private long totalHeapSizeBytes;
    private int totalObjectsCount;
    private int totalClassesCount;
    private int gcRootsCount;
    private List<ClassHistogramEntry> classHistogram;
    private List<DominatorSummaryEntry> topDominators;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassHistogramEntry {
        private String className;
        private int objectCount;
        private long totalShallowSizeBytes;
        private long totalRetainedSizeBytes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DominatorSummaryEntry {
        private long objectId;
        private String className;
        private long shallowSizeBytes;
        private long retainedSizeBytes;
        private double retainedPercentage;
        private int dominatedChildCount;
    }
}
