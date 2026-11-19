package com.shadowbase.backend.heap.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a Java Class definition loaded in the heap dump.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeapClass {

    /**
     * Unique 64-bit object ID of the Class object.
     */
    private long classId;

    /**
     * Fully qualified class name (e.g., "java.lang.String", "com.shadowbase.model.User").
     */
    private String name;

    /**
     * Object ID of the superclass.
     */
    private long superclassId;

    /**
     * Object ID of the class loader.
     */
    private long classLoaderId;

    /**
     * Shallow size of an instance of this class in bytes.
     */
    private int instanceSize;

    /**
     * List of instance field descriptors declared on this class.
     */
    @Builder.Default
    private List<HeapField> instanceFields = new ArrayList<>();

    /**
     * Object IDs referenced by static fields of this class.
     */
    @Builder.Default
    private List<Long> staticReferences = new ArrayList<>();

    public void addInstanceField(HeapField field) {
        if (this.instanceFields == null) {
            this.instanceFields = new ArrayList<>();
        }
        this.instanceFields.add(field);
    }

    public void addStaticReference(long targetId) {
        if (targetId != 0) {
            if (this.staticReferences == null) {
                this.staticReferences = new ArrayList<>();
            }
            this.staticReferences.add(targetId);
        }
    }
}
