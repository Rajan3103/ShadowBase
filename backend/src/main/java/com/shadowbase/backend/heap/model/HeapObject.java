package com.shadowbase.backend.heap.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a discrete object instance or array allocated on the Java heap.
 * Similar to Eclipse MAT's {@code IObject} or Jhat's {@code JavaObject}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeapObject {

    /**
     * Unique 64-bit address / identifier of the object on the heap.
     */
    private long objectId;

    /**
     * Object ID of the object's class.
     */
    private long classId;

    /**
     * Cached name of the object's class.
     */
    private String className;

    /**
     * Shallow size in bytes: memory directly allocated for this individual object.
     */
    private long shallowSize;

    /**
     * Retained size in bytes: total heap size freed if this object were garbage collected.
     * Computed via the Dominator Tree algorithm.
     */
    private long retainedSize;

    /**
     * List of object IDs directly referenced by this object (outgoing edges).
     */
    @Builder.Default
    private List<Long> outReferences = new ArrayList<>();

    /**
     * List of object IDs that reference this object (incoming edges).
     */
    @Builder.Default
    private List<Long> inReferences = new ArrayList<>();

    /**
     * Whether this object is an array.
     */
    private boolean array;

    /**
     * Number of elements if this is an array.
     */
    private int arrayLength;

    /**
     * Whether this object is a primitive array (e.g. byte[], int[]).
     */
    private boolean primitiveArray;

    /**
     * True if this object is registered as a GC Root.
     */
    private boolean gcRoot;

    /**
     * The GC Root classification if this object is a root.
     */
    private GCRootType rootType;

    public void addOutReference(long targetObjectId) {
        if (targetObjectId != 0 && targetObjectId != this.objectId) {
            if (this.outReferences == null) {
                this.outReferences = new ArrayList<>();
            }
            this.outReferences.add(targetObjectId);
        }
    }

    public void addInReference(long sourceObjectId) {
        if (sourceObjectId != 0 && sourceObjectId != this.objectId) {
            if (this.inReferences == null) {
                this.inReferences = new ArrayList<>();
            }
            this.inReferences.add(sourceObjectId);
        }
    }
}
