package com.shadowbase.backend.heap.algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.shadowbase.backend.heap.model.GCRoot;
import com.shadowbase.backend.heap.model.GCRootType;
import com.shadowbase.backend.heap.model.HeapClass;
import com.shadowbase.backend.heap.model.HeapField;
import com.shadowbase.backend.heap.model.HeapObject;
import com.shadowbase.backend.heap.model.HeapSnapshot;
import com.shadowbase.backend.heap.model.ReferenceChain;
import com.shadowbase.backend.heap.model.ReferenceChain.ReferenceHop;

import lombok.extern.slf4j.Slf4j;

/**
 * Algorithm for calculating reference chains connecting GC Roots to target objects.
 * <p>
 * Performs bidirectional/backwards Breadth-First Search (BFS) to identify the shortest
 * retention path preventing an object from being reclaimed by the Garbage Collector.
 */
@Slf4j
@Component
public class ReferenceChainFinder {

    /**
     * Finds the shortest path / reference chain from any GC Root down to the target object.
     *
     * @param snapshot       the analyzed HeapSnapshot
     * @param targetObjectId the object to trace
     * @return Optional containing the ReferenceChain if reachable from a GC root
     */
    public Optional<ReferenceChain> findShortestReferenceChain(HeapSnapshot snapshot, long targetObjectId) {
        if (snapshot == null) {
            return Optional.empty();
        }

        HeapObject targetObj = snapshot.getObject(targetObjectId);
        if (targetObj == null) {
            return Optional.empty();
        }

        // Fast-path: target object is itself a GC Root
        Map<Long, GCRoot> rootsById = new HashMap<>();
        for (GCRoot root : snapshot.getGcRoots()) {
            rootsById.put(root.getObjectId(), root);
        }

        if (rootsById.containsKey(targetObjectId)) {
            GCRoot root = rootsById.get(targetObjectId);
            return Optional.of(ReferenceChain.builder()
                    .targetObjectId(targetObjectId)
                    .targetClassName(targetObj.getClassName())
                    .rootObjectId(targetObjectId)
                    .rootType(root.getType())
                    .rootContext(root.getContext())
                    .hops(Collections.emptyList())
                    .length(0)
                    .build());
        }

        // Backwards BFS from target object along incoming references (inReferences)
        Queue<Long> queue = new ArrayDeque<>();
        Map<Long, Long> parentMap = new HashMap<>();
        Set<Long> visited = new HashSet<>();

        queue.add(targetObjectId);
        visited.add(targetObjectId);

        Long reachedRootId = null;

        while (!queue.isEmpty()) {
            long currentId = queue.poll();

            if (rootsById.containsKey(currentId)) {
                reachedRootId = currentId;
                break;
            }

            HeapObject currentObj = snapshot.getObject(currentId);
            if (currentObj == null) {
                continue;
            }

            for (long predecessorId : currentObj.getInReferences()) {
                if (!visited.contains(predecessorId)) {
                    visited.add(predecessorId);
                    parentMap.put(predecessorId, currentId);
                    queue.add(predecessorId);

                    if (rootsById.containsKey(predecessorId)) {
                        reachedRootId = predecessorId;
                        break;
                    }
                }
            }

            if (reachedRootId != null) {
                break;
            }
        }

        if (reachedRootId == null) {
            log.debug("Target object 0x{} has no path to any registered GC root", Long.toHexString(targetObjectId));
            return Optional.empty();
        }

        // Reconstruct forward path from root to target
        List<Long> pathIds = new ArrayList<>();
        Long curr = reachedRootId;
        while (curr != null && curr != targetObjectId) {
            pathIds.add(curr);
            curr = parentMap.get(curr);
        }
        pathIds.add(targetObjectId);

        // Build detailed reference hops
        List<ReferenceHop> hops = new ArrayList<>();
        for (int i = 0; i < pathIds.size() - 1; i++) {
            long fromId = pathIds.get(i);
            long toId = pathIds.get(i + 1);

            HeapObject fromObj = snapshot.getObject(fromId);
            HeapObject toObj = snapshot.getObject(toId);

            String fromName = fromObj != null ? fromObj.getClassName() : "Class_0x" + Long.toHexString(fromId);
            String toName = toObj != null ? toObj.getClassName() : "Object_0x" + Long.toHexString(toId);

            String fieldName = resolveFieldName(snapshot, fromObj, toId);

            hops.add(ReferenceHop.builder()
                    .fromObjectId(fromId)
                    .fromClassName(fromName)
                    .toObjectId(toId)
                    .toClassName(toName)
                    .fieldName(fieldName)
                    .build());
        }

        GCRoot rootInfo = rootsById.get(reachedRootId);

        return Optional.of(ReferenceChain.builder()
                .targetObjectId(targetObjectId)
                .targetClassName(targetObj.getClassName())
                .rootObjectId(reachedRootId)
                .rootType(rootInfo != null ? rootInfo.getType() : GCRootType.UNKNOWN)
                .rootContext(rootInfo != null ? rootInfo.getContext() : null)
                .hops(hops)
                .length(hops.size())
                .build());
    }

    /**
     * Resolves the field name or reference origin connecting fromObj to toId.
     */
    private String resolveFieldName(HeapSnapshot snapshot, HeapObject fromObj, long toId) {
        if (fromObj == null) {
            return "staticRef";
        }
        if (fromObj.isArray()) {
            return "[index]";
        }
        HeapClass clazz = snapshot.getClass(fromObj.getClassId());
        if (clazz != null && clazz.getInstanceFields() != null) {
            for (HeapField f : clazz.getInstanceFields()) {
                if (f.isReference()) {
                    return f.getName();
                }
            }
        }
        return "ref";
    }
}
