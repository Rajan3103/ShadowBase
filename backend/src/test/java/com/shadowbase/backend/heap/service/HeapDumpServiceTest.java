package com.shadowbase.backend.heap.service;

import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.heap.algorithm.DominatorTreeCalculator;
import com.shadowbase.backend.heap.algorithm.ReferenceChainFinder;
import com.shadowbase.backend.heap.dto.HeapAnalysisSummaryResponse;
import com.shadowbase.backend.heap.dto.ReferenceChainResponse;
import com.shadowbase.backend.heap.dto.RetainedSizeResponse;
import com.shadowbase.backend.heap.model.GCRoot;
import com.shadowbase.backend.heap.model.GCRootType;
import com.shadowbase.backend.heap.model.HeapObject;
import com.shadowbase.backend.heap.model.HeapSnapshot;
import com.shadowbase.backend.heap.model.ReferenceChain;
import com.shadowbase.backend.heap.parser.HprofParser;

@ExtendWith(MockitoExtension.class)
class HeapDumpServiceTest {

    @Mock
    private HprofParser parser;

    @Mock
    private DominatorTreeCalculator dominatorCalculator;

    @Mock
    private ReferenceChainFinder chainFinder;

    private HeapDumpServiceImpl service;
    private HeapSnapshot mockSnapshot;

    @BeforeEach
    void setUp() {
        service = new HeapDumpServiceImpl(parser, dominatorCalculator, chainFinder);

        mockSnapshot = new HeapSnapshot();
        mockSnapshot.setSnapshotId("snap-123");
        mockSnapshot.setHeaderVersion("JAVA PROFILE 1.0.2");

        HeapObject root = HeapObject.builder().objectId(1L).className("Root").shallowSize(50).retainedSize(150).build();
        HeapObject target = HeapObject.builder().objectId(2L).className("Target").shallowSize(100).retainedSize(100).build();

        mockSnapshot.addObject(root);
        mockSnapshot.addObject(target);
        mockSnapshot.addGCRoot(GCRoot.builder().objectId(1L).type(GCRootType.JAVA_FRAME).build());
    }

    @Test
    void testParseAndAnalyze_Success() throws Exception {
        InputStream mockStream = InputStream.nullInputStream();
        when(parser.parse(any(InputStream.class))).thenReturn(mockSnapshot);

        HeapSnapshot result = service.parseAndAnalyze(mockStream);

        assertNotNull(result);
        assertEquals("snap-123", result.getSnapshotId());
        verify(dominatorCalculator).calculateRetainedSizes(mockSnapshot);
    }

    @Test
    void testGetAnalysisSummary_Success() throws Exception {
        when(parser.parse(any(InputStream.class))).thenReturn(mockSnapshot);
        service.parseAndAnalyze(InputStream.nullInputStream());

        HeapAnalysisSummaryResponse summary = service.getAnalysisSummary("snap-123");

        assertNotNull(summary);
        assertEquals("snap-123", summary.getSnapshotId());
        assertEquals(2, summary.getTotalObjectsCount());
        assertEquals(1, summary.getGcRootsCount());
    }

    @Test
    void testGetObjectRetainedSize_Success() throws Exception {
        when(parser.parse(any(InputStream.class))).thenReturn(mockSnapshot);
        service.parseAndAnalyze(InputStream.nullInputStream());

        RetainedSizeResponse res = service.getObjectRetainedSize("snap-123", 2L);

        assertNotNull(res);
        assertEquals(2L, res.getObjectId());
        assertEquals(100, res.getShallowSizeBytes());
        assertEquals(100, res.getRetainedSizeBytes());
    }

    @Test
    void testGetReferenceChain_Success() throws Exception {
        when(parser.parse(any(InputStream.class))).thenReturn(mockSnapshot);
        service.parseAndAnalyze(InputStream.nullInputStream());

        ReferenceChain chain = ReferenceChain.builder()
                .targetObjectId(2L)
                .targetClassName("Target")
                .rootObjectId(1L)
                .rootType(GCRootType.JAVA_FRAME)
                .length(1)
                .hops(java.util.Collections.emptyList())
                .build();

        when(chainFinder.findShortestReferenceChain(eq(mockSnapshot), eq(2L))).thenReturn(Optional.of(chain));

        ReferenceChainResponse res = service.getReferenceChain("snap-123", 2L);

        assertNotNull(res);
        assertEquals(2L, res.getTargetObjectId());
        assertEquals(1L, res.getRootObjectId());
        assertEquals("JAVA_FRAME", res.getRootType());
    }

    @Test
    void testGetSnapshot_NotFound_ThrowsException() {
        assertThrows(ResourceNotFoundException.class, () -> service.getSnapshot("non-existent"));
    }
}
