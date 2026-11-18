package com.shadowbase.backend.heap.controller;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.shadowbase.backend.heap.dto.HeapAnalysisSummaryResponse;
import com.shadowbase.backend.heap.dto.ReferenceChainResponse;
import com.shadowbase.backend.heap.dto.RetainedSizeResponse;
import com.shadowbase.backend.heap.model.DominatorNode;
import com.shadowbase.backend.heap.model.HeapSnapshot;
import com.shadowbase.backend.heap.service.HeapDumpService;

@ExtendWith(MockitoExtension.class)
class HeapDumpControllerTest {

    @Mock
    private HeapDumpService heapDumpService;

    private HeapDumpController controller;

    @BeforeEach
    void setUp() {
        controller = new HeapDumpController(heapDumpService);
    }

    @Test
    void testAnalyzeHeapDump_Returns201Created() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "heap.hprof", "application/octet-stream", "dummy".getBytes());
        HeapSnapshot snapshot = new HeapSnapshot();
        snapshot.setSnapshotId("snap-abc");

        HeapAnalysisSummaryResponse summary = HeapAnalysisSummaryResponse.builder()
                .snapshotId("snap-abc")
                .totalObjectsCount(10)
                .build();

        when(heapDumpService.parseAndAnalyze(any(InputStream.class))).thenReturn(snapshot);
        when(heapDumpService.getAnalysisSummary("snap-abc")).thenReturn(summary);

        ResponseEntity<HeapAnalysisSummaryResponse> response = controller.analyzeHeapDump(file);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("snap-abc", response.getBody().getSnapshotId());
    }

    @Test
    void testGetSummary_Returns200Ok() {
        HeapAnalysisSummaryResponse summary = HeapAnalysisSummaryResponse.builder()
                .snapshotId("snap-abc")
                .totalObjectsCount(5)
                .build();

        when(heapDumpService.getAnalysisSummary("snap-abc")).thenReturn(summary);

        ResponseEntity<HeapAnalysisSummaryResponse> response = controller.getSummary("snap-abc");

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("snap-abc", response.getBody().getSnapshotId());
    }

    @Test
    void testGetObjectRetainedSize_Returns200Ok() {
        RetainedSizeResponse res = RetainedSizeResponse.builder()
                .objectId(12345L)
                .className("java.lang.String")
                .retainedSizeBytes(512)
                .build();

        when(heapDumpService.getObjectRetainedSize("snap-abc", 12345L)).thenReturn(res);

        ResponseEntity<RetainedSizeResponse> response = controller.getObjectRetainedSize("snap-abc", 12345L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(12345L, response.getBody().getObjectId());
    }

    @Test
    void testGetReferenceChain_Returns200Ok() {
        ReferenceChainResponse res = ReferenceChainResponse.builder()
                .targetObjectId(999L)
                .rootObjectId(1L)
                .hopsCount(2)
                .hops(Collections.emptyList())
                .build();

        when(heapDumpService.getReferenceChain("snap-abc", 999L)).thenReturn(res);

        ResponseEntity<ReferenceChainResponse> response = controller.getReferenceChain("snap-abc", 999L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(999L, response.getBody().getTargetObjectId());
    }

    @Test
    void testGetDominators_Returns200Ok() {
        DominatorNode node = DominatorNode.builder().objectId(100L).retainedSize(1024).build();
        when(heapDumpService.getDominatorTree("snap-abc", 10)).thenReturn(List.of(node));

        ResponseEntity<List<DominatorNode>> response = controller.getDominators("snap-abc", 10);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(heapDumpService).getDominatorTree("snap-abc", 10);
    }
}
