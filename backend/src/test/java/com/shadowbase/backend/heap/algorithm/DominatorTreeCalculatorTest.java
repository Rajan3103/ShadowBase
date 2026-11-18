package com.shadowbase.backend.heap.algorithm;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shadowbase.backend.heap.model.DominatorNode;
import com.shadowbase.backend.heap.model.GCRoot;
import com.shadowbase.backend.heap.model.GCRootType;
import com.shadowbase.backend.heap.model.HeapObject;
import com.shadowbase.backend.heap.model.HeapSnapshot;

class DominatorTreeCalculatorTest {

    private DominatorTreeCalculator calculator;
    private HeapSnapshot snapshot;

    @BeforeEach
    void setUp() {
        calculator = new DominatorTreeCalculator();
        snapshot = new HeapSnapshot();
    }

    @Test
    void testLinearChain_RetainedSizesAccumulateCorrectly() {
        // Root (1) -> A (2) -> B (3)
        // Shallow: Root=10, A=20, B=30
        // Retained: B=30, A=50, Root=60
        HeapObject root = HeapObject.builder().objectId(1L).className("Root").shallowSize(10).build();
        HeapObject a = HeapObject.builder().objectId(2L).className("A").shallowSize(20).build();
        HeapObject b = HeapObject.builder().objectId(3L).className("B").shallowSize(30).build();

        root.addOutReference(2L);
        a.addOutReference(3L);

        snapshot.addObject(root);
        snapshot.addObject(a);
        snapshot.addObject(b);
        snapshot.addGCRoot(GCRoot.builder().objectId(1L).type(GCRootType.JAVA_FRAME).build());
        snapshot.buildIncomingReferences();

        calculator.calculateRetainedSizes(snapshot);

        assertEquals(30, snapshot.getObject(3L).getRetainedSize(), "B retained size should equal its shallow size");
        assertEquals(50, snapshot.getObject(2L).getRetainedSize(), "A retained size should be A + B = 50");
        assertEquals(60, snapshot.getObject(1L).getRetainedSize(), "Root retained size should be Root + A + B = 60");

        DominatorNode domB = snapshot.getDominatorNode(3L);
        assertNotNull(domB);
        assertEquals(2L, domB.getImmediateDominatorId(), "A should be immediate dominator of B");

        DominatorNode domA = snapshot.getDominatorNode(2L);
        assertNotNull(domA);
        assertEquals(1L, domA.getImmediateDominatorId(), "Root should be immediate dominator of A");
    }

    @Test
    void testDiamondGraph_SharedNodeDominatedByRoot() {
        // Root (1) -> A (2) and B (3)
        // Both A (2) and B (3) -> Shared C (4)
        // Shallow: Root=10, A=20, B=20, C=40
        // Because C is reachable via both A and B, neither A nor B exclusively dominates C.
        // Therefore, immediate dominator of C is Root (1).
        // Retained: A=20, B=20, C=40, Root=90
        HeapObject root = HeapObject.builder().objectId(1L).className("Root").shallowSize(10).build();
        HeapObject a = HeapObject.builder().objectId(2L).className("A").shallowSize(20).build();
        HeapObject b = HeapObject.builder().objectId(3L).className("B").shallowSize(20).build();
        HeapObject c = HeapObject.builder().objectId(4L).className("C").shallowSize(40).build();

        root.addOutReference(2L);
        root.addOutReference(3L);
        a.addOutReference(4L);
        b.addOutReference(4L);

        snapshot.addObject(root);
        snapshot.addObject(a);
        snapshot.addObject(b);
        snapshot.addObject(c);
        snapshot.addGCRoot(GCRoot.builder().objectId(1L).type(GCRootType.JNI_GLOBAL).build());
        snapshot.buildIncomingReferences();

        calculator.calculateRetainedSizes(snapshot);

        assertEquals(40, snapshot.getObject(4L).getRetainedSize());
        assertEquals(20, snapshot.getObject(2L).getRetainedSize(), "A should not retain C because B also references C");
        assertEquals(20, snapshot.getObject(3L).getRetainedSize(), "B should not retain C because A also references C");
        assertEquals(90, snapshot.getObject(1L).getRetainedSize(), "Root retains all: 10 + 20 + 20 + 40 = 90");

        DominatorNode domC = snapshot.getDominatorNode(4L);
        assertNotNull(domC);
        assertEquals(1L, domC.getImmediateDominatorId(), "Immediate dominator of C must be Root (1)");
    }

    @Test
    void testCyclicReferences_HandledWithoutInfiniteLoop() {
        // Root (1) -> A (2) -> B (3) -> A (2) (cycle!)
        // Shallow: Root=10, A=20, B=30
        HeapObject root = HeapObject.builder().objectId(1L).className("Root").shallowSize(10).build();
        HeapObject a = HeapObject.builder().objectId(2L).className("A").shallowSize(20).build();
        HeapObject b = HeapObject.builder().objectId(3L).className("B").shallowSize(30).build();

        root.addOutReference(2L);
        a.addOutReference(3L);
        b.addOutReference(2L); // Cycle

        snapshot.addObject(root);
        snapshot.addObject(a);
        snapshot.addObject(b);
        snapshot.addGCRoot(GCRoot.builder().objectId(1L).type(GCRootType.THREAD_OBJECT).build());
        snapshot.buildIncomingReferences();

        calculator.calculateRetainedSizes(snapshot);

        assertEquals(60, snapshot.getObject(1L).getRetainedSize(), "Root retains cycle A-B: 10 + 20 + 30 = 60");
        assertEquals(50, snapshot.getObject(2L).getRetainedSize(), "A dominates B, so A retains A + B = 50");
        assertEquals(30, snapshot.getObject(3L).getRetainedSize(), "B does not dominate A (A is reached from Root)");
    }
}
