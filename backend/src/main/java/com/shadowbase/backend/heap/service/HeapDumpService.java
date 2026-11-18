package com.shadowbase.backend.heap.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.shadowbase.backend.heap.dto.HeapAnalysisSummaryResponse;
import com.shadowbase.backend.heap.dto.ReferenceChainResponse;
import com.shadowbase.backend.heap.dto.RetainedSizeResponse;
import com.shadowbase.backend.heap.model.DominatorNode;
import com.shadowbase.backend.heap.model.HeapSnapshot;

/**
 * Service orchestrating binary heap dump ingestion, dominator tree calculation,
 * retained size metrics, and reference chain tracing.
 */
public interface HeapDumpService {

    /**
     * Ingests, parses, and analyzes a binary HPROF input stream.
     *
     * @param inputStream binary HPROF stream
     * @return fully analyzed HeapSnapshot
     * @throws IOException on read/parse error
     */
    HeapSnapshot parseAndAnalyze(InputStream inputStream) throws IOException;

    /**
     * Ingests, parses, and analyzes an HPROF file from the filesystem.
     *
     * @param file binary HPROF file
     * @return fully analyzed HeapSnapshot
     * @throws IOException on read/parse error
     */
    HeapSnapshot parseAndAnalyze(File file) throws IOException;

    /**
     * Retrieves high-level memory summary and top dominators for an analyzed snapshot.
     *
     * @param snapshotId UUID string of the snapshot
     * @return HeapAnalysisSummaryResponse
     */
    HeapAnalysisSummaryResponse getAnalysisSummary(String snapshotId);

    /**
     * Calculates and returns the retained size metrics for a specific object.
     *
     * @param snapshotId UUID string of the snapshot
     * @param objectId   64-bit object identifier
     * @return RetainedSizeResponse
     */
    RetainedSizeResponse getObjectRetainedSize(String snapshotId, long objectId);

    /**
     * Finds the shortest reference chain / retention path from a GC Root to the specified object.
     *
     * @param snapshotId UUID string of the snapshot
     * @param objectId   64-bit object identifier
     * @return ReferenceChainResponse
     */
    ReferenceChainResponse getReferenceChain(String snapshotId, long objectId);

    /**
     * Retrieves the top dominator nodes in the Dominator Tree.
     *
     * @param snapshotId UUID string of the snapshot
     * @param limit      maximum entries to return
     * @return list of DominatorNode
     */
    List<DominatorNode> getDominatorTree(String snapshotId, int limit);

    /**
     * Returns the cached HeapSnapshot by ID.
     *
     * @param snapshotId UUID string of the snapshot
     * @return HeapSnapshot
     */
    HeapSnapshot getSnapshot(String snapshotId);
}
