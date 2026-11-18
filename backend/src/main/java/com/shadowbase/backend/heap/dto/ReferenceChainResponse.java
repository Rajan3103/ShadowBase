package com.shadowbase.backend.heap.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Traced reference chain from a GC root down to an object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceChainResponse {

    private long targetObjectId;
    private String targetClassName;
    private long rootObjectId;
    private String rootType;
    private String rootContext;
    private int hopsCount;
    private List<HopDto> hops;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HopDto {
        private long fromObjectId;
        private String fromClassName;
        private long toObjectId;
        private String toClassName;
        private String fieldName;
    }
}
