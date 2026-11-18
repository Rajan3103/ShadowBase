package com.shadowbase.backend.heap.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Garbage Collection (GC) Root in the heap graph.
 * GC roots are objects that are directly accessible from outside the heap
 * (e.g. stack variables, thread objects, JNI references).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GCRoot {

    /**
     * Unique 64-bit object identifier of the root.
     */
    private long objectId;

    /**
     * Classification of the root.
     */
    private GCRootType type;

    /**
     * Additional context (e.g., thread ID, stack frame number).
     */
    private String context;
}
