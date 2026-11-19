package com.shadowbase.backend.heap.algorithm;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shadowbase.backend.heap.model.GCRoot;
import com.shadowbase.backend.heap.model.GCRootType;
import com.shadowbase.backend.heap.model.HeapObject;
import com.shadowbase.backend.heap.model.HeapSnapshot;
import com.shadowbase.backend.heap.model.ReferenceChain;

class ReferenceChainFinderTest {

    private ReferenceChainFinder chainFinder;
    private HeapSnapshot snapshot;

    @BeforeEach
    void setUp() {
        chainFinder = new ReferenceChainFinder();
        snapshot = new HeapSnapshot();
    }

    @Test
    void testFindShortestReferenceChain_MultiHopPath() {
        // Root (100) -> Service (200) -> Cache (300) -> Target (400)
        HeapObject root = HeapObject.builder().objectId(100L).className("MainThread").build();
        HeapObject service = HeapObject.builder().objectId(200L).className("OrderService").build();
        HeapObject cache = HeapObject.builder().objectId(300L).className("HashMap").build();
        HeapObject target = HeapObject.builder().objectId(400L).className("LeakedConnection").build();

        root.addOutReference(200L);
        service.addOutReference(300L);
        cache.addOutReference(400L);

        snapshot.addObject(root);
        snapshot.addObject(service);
        snapshot.addObject(cache);
        snapshot.addObject(target);
        snapshot.addGCRoot(GCRoot.builder().objectId(100L).type(GCRootType.THREAD_OBJECT).context("thread=main").build());
        snapshot.buildIncomingReferences();

        Optional<ReferenceChain> optChain = chainFinder.findShortestReferenceChain(snapshot, 400L);

        assertTrue(optChain.isPresent());
        ReferenceChain chain = optChain.get();

        assertEquals(400L, chain.getTargetObjectId());
        assertEquals(100L, chain.getRootObjectId());
        assertEquals(GCRootType.THREAD_OBJECT, chain.getRootType());
        assertEquals(3, chain.getLength());
        assertEquals(3, chain.getHops().size());

        assertEquals(100L, chain.getHops().get(0).getFromObjectId());
        assertEquals(200L, chain.getHops().get(0).getToObjectId());

        assertEquals(200L, chain.getHops().get(1).getFromObjectId());
        assertEquals(300L, chain.getHops().get(1).getToObjectId());

        assertEquals(300L, chain.getHops().get(2).getFromObjectId());
        assertEquals(400L, chain.getHops().get(2).getToObjectId());
    }

    @Test
    void testFindShortestReferenceChain_TargetIsRoot() {
        HeapObject root = HeapObject.builder().objectId(500L).className("RootClass").build();
        snapshot.addObject(root);
        snapshot.addGCRoot(GCRoot.builder().objectId(500L).type(GCRootType.STICKY_CLASS).build());
        snapshot.buildIncomingReferences();

        Optional<ReferenceChain> optChain = chainFinder.findShortestReferenceChain(snapshot, 500L);

        assertTrue(optChain.isPresent());
        ReferenceChain chain = optChain.get();
        assertEquals(500L, chain.getTargetObjectId());
        assertEquals(500L, chain.getRootObjectId());
        assertEquals(0, chain.getLength());
    }

    @Test
    void testFindShortestReferenceChain_UnreachableObject() {
        HeapObject orphan = HeapObject.builder().objectId(999L).className("DeadObject").build();
        snapshot.addObject(orphan);
        snapshot.buildIncomingReferences();

        Optional<ReferenceChain> optChain = chainFinder.findShortestReferenceChain(snapshot, 999L);
        assertFalse(optChain.isPresent(), "Orphan object should not have a reference chain to any GC root");
    }
}
