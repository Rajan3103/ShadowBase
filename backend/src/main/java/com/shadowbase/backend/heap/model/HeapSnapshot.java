package com.shadowbase.backend.heap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

/**
 * Representation of an analyzed Java heap dump, providing an API
 * conceptually aligned with Eclipse MAT's {@code ISnapshot} and Jhat's {@code JavaHeap}.
 */
@Getter
@Setter
public class HeapSnapshot {

    /**
     * Unique identifier for this snapshot instance.
     */
    private String snapshotId;

    /**
     * Header string from HPROF (e.g. "JAVA PROFILE 1.0.2").
     */
    private String headerVersion;

    /**
     * Object identifier size in bytes (4 for 32-bit, 8 for 64-bit).
     */
    private int idSize = 8;

    /**
     * Dump creation timestamp.
     */
    private long dumpTimestamp;

    /**
     * Total memory allocated across all parsed heap objects in bytes.
     */
    private long totalHeapSize;

    /**
     * Map of object ID to HeapObject.
     */
    private final Map<Long, HeapObject> objects = new HashMap<>();

    /**
     * Map of class ID to HeapClass.
     */
    private final Map<Long, HeapClass> classes = new HashMap<>();

    /**
     * Map of fully qualified class name to HeapClass.
     */
    private final Map<String, HeapClass> classesByName = new HashMap<>();

    /**
     * Registered Garbage Collection Roots.
     */
    private final List<GCRoot> gcRoots = new ArrayList<>();

    /**
     * Dominator tree mapping object ID to DominatorNode.
     */
    private final Map<Long, DominatorNode> dominatorTree = new HashMap<>();

    public void addObject(HeapObject object) {
        if (object != null) {
            objects.put(object.getObjectId(), object);
            totalHeapSize += object.getShallowSize();
        }
    }

    public void addClass(HeapClass heapClass) {
        if (heapClass != null) {
            classes.put(heapClass.getClassId(), heapClass);
            if (heapClass.getName() != null) {
                classesByName.put(heapClass.getName(), heapClass);
            }
        }
    }

    public void addGCRoot(GCRoot root) {
        if (root != null) {
            gcRoots.add(root);
            HeapObject obj = objects.get(root.getObjectId());
            if (obj != null) {
                obj.setGcRoot(true);
                obj.setRootType(root.getType());
            }
        }
    }

    public HeapObject getObject(long objectId) {
        return objects.get(objectId);
    }

    public HeapClass getClass(long classId) {
        return classes.get(classId);
    }

    public HeapClass getClassByName(String className) {
        return classesByName.get(className);
    }

    public List<HeapObject> getObjectsByClassName(String className) {
        HeapClass clazz = classesByName.get(className);
        if (clazz == null) {
            return Collections.emptyList();
        }
        return objects.values().stream()
                .filter(obj -> obj.getClassId() == clazz.getClassId())
                .collect(Collectors.toList());
    }

    public DominatorNode getDominatorNode(long objectId) {
        return dominatorTree.get(objectId);
    }

    /**
     * Returns top objects by retained size (descending).
     *
     * @param limit max count
     * @return sorted list of HeapObjects
     */
    public List<HeapObject> getTopRetainedObjects(int limit) {
        return objects.values().stream()
                .sorted(Comparator.comparingLong(HeapObject::getRetainedSize).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Builds incoming reference index (inReferences) across all objects.
     */
    public void buildIncomingReferences() {
        for (HeapObject obj : objects.values()) {
            for (long targetId : obj.getOutReferences()) {
                HeapObject target = objects.get(targetId);
                if (target != null) {
                    target.addInReference(obj.getObjectId());
                }
            }
        }
        // Also index static references from classes
        for (HeapClass clazz : classes.values()) {
            for (long targetId : clazz.getStaticReferences()) {
                HeapObject target = objects.get(targetId);
                if (target != null) {
                    target.addInReference(clazz.getClassId());
                }
            }
        }
    }
}
