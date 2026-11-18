package com.shadowbase.backend.heap.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Node in the Dominator Tree of the heap graph.
 * <p>
 * Object A dominates Object B if every execution path from GC Roots to B must pass through A.
 * If A is garbage-collected, all objects in its dominated subtree are also garbage-collected.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DominatorNode {

    /**
     * The dominated object's ID.
     */
    private long objectId;

    /**
     * Class name of the object.
     */
    private String className;

    /**
     * Object ID of the immediate dominator (parent in the dominator tree).
     * 0 represents the synthetic Super-Root (GC Root level).
     */
    private long immediateDominatorId;

    /**
     * Memory directly consumed by this object.
     */
    private long shallowSize;

    /**
     * Total memory retained by this object (itself plus all dominated descendants).
     */
    private long retainedSize;

    /**
     * Objects directly dominated by this node.
     */
    @Builder.Default
    private List<Long> dominatedChildren = new ArrayList<>();

    public void addDominatedChild(long childId) {
        if (this.dominatedChildren == null) {
            this.dominatedChildren = new ArrayList<>();
        }
        this.dominatedChildren.add(childId);
    }
}
