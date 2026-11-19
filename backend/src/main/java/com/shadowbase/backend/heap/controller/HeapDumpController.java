package com.shadowbase.backend.heap.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shadowbase.backend.heap.dto.HeapAnalysisSummaryResponse;
import com.shadowbase.backend.heap.dto.ReferenceChainResponse;
import com.shadowbase.backend.heap.dto.RetainedSizeResponse;
import com.shadowbase.backend.heap.model.DominatorNode;
import com.shadowbase.backend.heap.model.HeapSnapshot;
import com.shadowbase.backend.heap.service.HeapDumpService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller exposing binary heap dump analysis, retained size calculation,
 * and reference chain resolution endpoints.
 * <p>
 * Base URI: {@code /api/heap-dumps}
 */
@Slf4j
@RestController
@RequestMapping("/api/heap-dumps")
public class HeapDumpController {

    private final HeapDumpService heapDumpService;

    public HeapDumpController(HeapDumpService heapDumpService) {
        this.heapDumpService = heapDumpService;
    }

    /**
     * Uploads and analyzes a binary HPROF heap dump file.
     * Computes the Dominator Tree and retained sizes across all objects.
     *
     * @param file the binary HPROF multipart file
     * @return 201 CREATED with HeapAnalysisSummaryResponse
     * @throws IOException on I/O error reading the file stream
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HeapAnalysisSummaryResponse> analyzeHeapDump(
            @RequestParam("file") MultipartFile file) throws IOException {

        log.info("REST request to analyze binary heap dump: {} (size: {} bytes)",
                file.getOriginalFilename(), file.getSize());

        HeapSnapshot snapshot = heapDumpService.parseAndAnalyze(file.getInputStream());
        HeapAnalysisSummaryResponse summary = heapDumpService.getAnalysisSummary(snapshot.getSnapshotId());

        return new ResponseEntity<>(summary, HttpStatus.CREATED);
    }

    /**
     * Retrieves the high-level analysis summary for a previously analyzed snapshot.
     *
     * @param snapshotId UUID string of the snapshot
     * @return 200 OK with HeapAnalysisSummaryResponse
     */
    @GetMapping("/{snapshotId}/summary")
    public ResponseEntity<HeapAnalysisSummaryResponse> getSummary(
            @PathVariable("snapshotId") String snapshotId) {

        return ResponseEntity.ok(heapDumpService.getAnalysisSummary(snapshotId));
    }

    /**
     * Retrieves retained size metrics and dominator information for a specific object.
     *
     * @param snapshotId UUID string of the snapshot
     * @param objectId   64-bit object identifier
     * @return 200 OK with RetainedSizeResponse
     */
    @GetMapping("/{snapshotId}/objects/{objectId}/retained-size")
    public ResponseEntity<RetainedSizeResponse> getObjectRetainedSize(
            @PathVariable("snapshotId") String snapshotId,
            @PathVariable("objectId") long objectId) {

        return ResponseEntity.ok(heapDumpService.getObjectRetainedSize(snapshotId, objectId));
    }

    /**
     * Finds and returns the shortest reference chain / retention path
     * from a GC Root to the specified object.
     *
     * @param snapshotId UUID string of the snapshot
     * @param objectId   64-bit object identifier
     * @return 200 OK with ReferenceChainResponse
     */
    @GetMapping("/{snapshotId}/objects/{objectId}/reference-chain")
    public ResponseEntity<ReferenceChainResponse> getReferenceChain(
            @PathVariable("snapshotId") String snapshotId,
            @PathVariable("objectId") long objectId) {

        return ResponseEntity.ok(heapDumpService.getReferenceChain(snapshotId, objectId));
    }

    /**
     * Retrieves top memory dominators from the Dominator Tree.
     *
     * @param snapshotId UUID string of the snapshot
     * @param limit      optional maximum entries to return (default 50)
     * @return 200 OK with list of DominatorNode
     */
    @GetMapping("/{snapshotId}/dominators")
    public ResponseEntity<List<DominatorNode>> getDominators(
            @PathVariable("snapshotId") String snapshotId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        return ResponseEntity.ok(heapDumpService.getDominatorTree(snapshotId, limit));
    }
}
