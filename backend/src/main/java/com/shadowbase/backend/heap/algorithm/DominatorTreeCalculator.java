package com.shadowbase.backend.heap.algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.shadowbase.backend.heap.model.DominatorNode;
import com.shadowbase.backend.heap.model.GCRoot;
import com.shadowbase.backend.heap.model.HeapObject;
import com.shadowbase.backend.heap.model.HeapSnapshot;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes immediate dominators and object retained sizes across the heap graph
 * using the Cooper-Harvey-Kennedy iterative dominator algorithm.
 * <p>
 * Retained size represents the total amount of memory that would be reclaimed
 * by the garbage collector if a given object were freed.
 */
@Slf4j
@Component
public class DominatorTreeCalculator {

    private static final long SUPER_ROOT_ID = 0L;

    /**
     * Calculates the dominator tree and assigns retained sizes to all objects in the snapshot.
     *
     * @param snapshot the parsed HeapSnapshot
     */
    public void calculateRetainedSizes(HeapSnapshot snapshot) {
        if (snapshot == null || snapshot.getObjects().isEmpty()) {
            return;
        }

        Map<Long, HeapObject> objects = snapshot.getObjects();
        List<GCRoot> gcRoots = snapshot.getGcRoots();

        // 1. Build adjacency list for reachability from Super-Root (0)
        Map<Long, List<Long>> outgoingEdges = new HashMap<>();
        Map<Long, List<Long>> incomingEdges = new HashMap<>();

        // Connect Super-Root to all GC roots
        List<Long> rootTargets = new ArrayList<>();
        for (GCRoot root : gcRoots) {
            if (objects.containsKey(root.getObjectId())) {
                rootTargets.add(root.getObjectId());
            }
        }
        // If no explicit roots found, treat objects with no incoming references as candidate roots
        if (rootTargets.isEmpty()) {
            for (HeapObject obj : objects.values()) {
                if (obj.getInReferences().isEmpty()) {
                    rootTargets.add(obj.getObjectId());
                }
            }
        }
        outgoingEdges.put(SUPER_ROOT_ID, rootTargets);

        for (HeapObject obj : objects.values()) {
            List<Long> validOut = new ArrayList<>();
            for (long targetId : obj.getOutReferences()) {
                if (objects.containsKey(targetId) && targetId != obj.getObjectId()) {
                    validOut.add(targetId);
                }
            }
            outgoingEdges.put(obj.getObjectId(), validOut);
        }

        // 2. Discover reachable nodes via DFS from SUPER_ROOT_ID and compute Reverse Post-Order (RPO)
        List<Long> rpoList = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> postOrder = new ArrayDeque<>();

        dfsPostOrder(SUPER_ROOT_ID, outgoingEdges, visited, postOrder);

        while (!postOrder.isEmpty()) {
            rpoList.add(postOrder.pop());
        }

        int nodeCount = rpoList.size();
        if (nodeCount <= 1) {
            // Only super root or no reachable nodes
            for (HeapObject obj : objects.values()) {
                obj.setRetainedSize(obj.getShallowSize());
            }
            return;
        }

        // Map objectId -> RPO index (0 to nodeCount - 1)
        Map<Long, Integer> rpoIndex = new HashMap<>(nodeCount);
        long[] idByIndex = new long[nodeCount];
        for (int i = 0; i < nodeCount; i++) {
            long id = rpoList.get(i);
            rpoIndex.put(id, i);
            idByIndex[i] = id;
        }

        // Build predecessor lists using integer indices
        List<List<Integer>> predecessors = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            predecessors.add(new ArrayList<>());
        }

        for (int u = 0; u < nodeCount; u++) {
            long uId = idByIndex[u];
            List<Long> targets = outgoingEdges.getOrDefault(uId, Collections.emptyList());
            for (long vId : targets) {
                Integer v = rpoIndex.get(vId);
                if (v != null) {
                    predecessors.get(v).add(u);
                }
            }
        }

        // 3. Iterative Dominator computation
        int[] idoms = new int[nodeCount];
        Arrays.fill(idoms, -1);
        idoms[0] = 0; // Super root dominates itself

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int b = 1; b < nodeCount; b++) {
                List<Integer> preds = predecessors.get(b);
                if (preds.isEmpty()) {
                    continue;
                }

                // Pick first processed predecessor
                int newIdom = -1;
                for (int p : preds) {
                    if (idoms[p] != -1) {
                        newIdom = p;
                        break;
                    }
                }

                if (newIdom != -1) {
                    for (int p : preds) {
                        if (p != newIdom && idoms[p] != -1) {
                            newIdom = intersect(p, newIdom, idoms);
                        }
                    }

                    if (idoms[b] != newIdom) {
                        idoms[b] = newIdom;
                        changed = true;
                    }
                }
            }
        }

        // 4. Construct Dominator Tree adjacency list
        Map<Long, List<Long>> dominatorChildren = new HashMap<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            dominatorChildren.put(idByIndex[i], new ArrayList<>());
        }

        for (int b = 1; b < nodeCount; b++) {
            int p = idoms[b];
            if (p != -1) {
                long parentId = idByIndex[p];
                long childId = idByIndex[b];
                dominatorChildren.get(parentId).add(childId);
            }
        }

        // 5. Calculate retained sizes bottom-up (post-order traversal of dominator tree)
        Map<Long, Long> retainedSizes = new HashMap<>(nodeCount);
        computeRetainedSizesDfs(SUPER_ROOT_ID, dominatorChildren, objects, retainedSizes);

        // 6. Populate results in snapshot and DominatorNode tree
        for (int b = 1; b < nodeCount; b++) {
            long objId = idByIndex[b];
            HeapObject obj = objects.get(objId);
            if (obj != null) {
                long retained = retainedSizes.getOrDefault(objId, obj.getShallowSize());
                obj.setRetainedSize(retained);

                int p = idoms[b];
                long parentId = (p != -1) ? idByIndex[p] : SUPER_ROOT_ID;

                DominatorNode node = DominatorNode.builder()
                        .objectId(objId)
                        .className(obj.getClassName())
                        .immediateDominatorId(parentId)
                        .shallowSize(obj.getShallowSize())
                        .retainedSize(retained)
                        .dominatedChildren(dominatorChildren.getOrDefault(objId, Collections.emptyList()))
                        .build();

                snapshot.getDominatorTree().put(objId, node);
            }
        }

        // For any unreachable objects, retained size = shallow size
        for (HeapObject obj : objects.values()) {
            if (!rpoIndex.containsKey(obj.getObjectId())) {
                obj.setRetainedSize(obj.getShallowSize());
            }
        }

        log.info("Calculated retained sizes and dominator tree for {} reachable heap objects", nodeCount - 1);
    }

    private int intersect(int finger1, int finger2, int[] idoms) {
        while (finger1 != finger2) {
            while (finger1 > finger2) {
                finger1 = idoms[finger1];
            }
            while (finger2 > finger1) {
                finger2 = idoms[finger2];
            }
        }
        return finger1;
    }

    private void dfsPostOrder(
            long node,
            Map<Long, List<Long>> outgoingEdges,
            Set<Long> visited,
            Deque<Long> postOrder) {

        visited.add(node);
        List<Long> neighbors = outgoingEdges.getOrDefault(node, Collections.emptyList());
        for (long neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                dfsPostOrder(neighbor, outgoingEdges, visited, postOrder);
            }
        }
        postOrder.push(node);
    }

    private long computeRetainedSizesDfs(
            long node,
            Map<Long, List<Long>> dominatorChildren,
            Map<Long, HeapObject> objects,
            Map<Long, Long> retainedSizes) {

        long totalRetained = 0;
        HeapObject obj = objects.get(node);
        if (obj != null) {
            totalRetained += obj.getShallowSize();
        }

        List<Long> children = dominatorChildren.getOrDefault(node, Collections.emptyList());
        for (long child : children) {
            totalRetained += computeRetainedSizesDfs(child, dominatorChildren, objects, retainedSizes);
        }

        if (node != SUPER_ROOT_ID) {
            retainedSizes.put(node, totalRetained);
        }

        return totalRetained;
    }
}
