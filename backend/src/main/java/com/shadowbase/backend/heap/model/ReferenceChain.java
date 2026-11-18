package com.shadowbase.backend.heap.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a reference path or chain connecting a GC Root to a target object.
 * Explains memory retention (i.e. why an object is not garbage-collected).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceChain {

    /**
     * The target object ID at the end of the chain.
     */
    private long targetObjectId;

    /**
     * Target object class name.
     */
    private String targetClassName;

    /**
     * The root object ID at the start of the chain.
     */
    private long rootObjectId;

    /**
     * GC Root classification.
     */
    private GCRootType rootType;

    /**
     * Additional root context (thread, frame, etc.).
     */
    private String rootContext;

    /**
     * Sequential list of hops from the GC Root down to the target object.
     */
    @Builder.Default
    private List<ReferenceHop> hops = new ArrayList<>();

    /**
     * Number of reference hops in the chain.
     */
    private int length;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReferenceHop {
        private long fromObjectId;
        private String fromClassName;
        private long toObjectId;
        private String toClassName;
        private String fieldName;
    }
}
