package com.shadowbase.backend.heap.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.heap.algorithm.DominatorTreeCalculator;
import com.shadowbase.backend.heap.algorithm.ReferenceChainFinder;
import com.shadowbase.backend.heap.dto.HeapAnalysisSummaryResponse;
import com.shadowbase.backend.heap.dto.HeapAnalysisSummaryResponse.ClassHistogramEntry;
import com.shadowbase.backend.heap.dto.HeapAnalysisSummaryResponse.DominatorSummaryEntry;
import com.shadowbase.backend.heap.dto.ReferenceChainResponse;
import com.shadowbase.backend.heap.dto.ReferenceChainResponse.HopDto;
import com.shadowbase.backend.heap.dto.RetainedSizeResponse;
import com.shadowbase.backend.heap.model.DominatorNode;
import com.shadowbase.backend.heap.model.HeapObject;
import com.shadowbase.backend.heap.model.HeapSnapshot;
import com.shadowbase.backend.heap.model.ReferenceChain;
import com.shadowbase.backend.heap.parser.HprofParser;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link HeapDumpService}.
 */
@Slf4j
@Service
public class HeapDumpServiceImpl implements HeapDumpService {

    private final HprofParser parser;
    private final DominatorTreeCalculator dominatorCalculator;
    private final ReferenceChainFinder chainFinder;

    private final Map<String, HeapSnapshot> snapshotCache = new ConcurrentHashMap<>();

    public HeapDumpServiceImpl(
            HprofParser parser,
            DominatorTreeCalculator dominatorCalculator,
            ReferenceChainFinder chainFinder) {
        this.parser = parser;
        this.dominatorCalculator = dominatorCalculator;
        this.chainFinder = chainFinder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HeapSnapshot parseAndAnalyze(InputStream inputStream) throws IOException {
        HeapSnapshot snapshot = parser.parse(inputStream);
        dominatorCalculator.calculateRetainedSizes(snapshot);
        snapshotCache.put(snapshot.getSnapshotId(), snapshot);
        log.info("Cached analyzed snapshot: {}", snapshot.getSnapshotId());
        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HeapSnapshot parseAndAnalyze(File file) throws IOException {
        HeapSnapshot snapshot = parser.parse(file);
        dominatorCalculator.calculateRetainedSizes(snapshot);
        snapshotCache.put(snapshot.getSnapshotId(), snapshot);
        log.info("Cached analyzed snapshot from file '{}': {}", file.getName(), snapshot.getSnapshotId());
        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HeapAnalysisSummaryResponse getAnalysisSummary(String snapshotId) {
        HeapSnapshot snapshot = getSnapshot(snapshotId);

        long totalHeapSize = Math.max(snapshot.getTotalHeapSize(), 1L);

        // Compute class histogram
        Map<String, ClassStats> statsByClass = new HashMap<>();
        for (HeapObject obj : snapshot.getObjects().values()) {
            String cName = obj.getClassName() != null ? obj.getClassName() : "Unknown";
            statsByClass.computeIfAbsent(cName, k -> new ClassStats())
                    .add(obj.getShallowSize(), obj.getRetainedSize());
        }

        List<ClassHistogramEntry> histogram = statsByClass.entrySet().stream()
                .map(e -> ClassHistogramEntry.builder()
                        .className(e.getKey())
                        .objectCount(e.getValue().count)
                        .totalShallowSizeBytes(e.getValue().shallowSize)
                        .totalRetainedSizeBytes(e.getValue().retainedSize)
                        .build())
                .sorted(Comparator.comparingLong(ClassHistogramEntry::getTotalRetainedSizeBytes).reversed())
                .limit(50)
                .collect(Collectors.toList());

        // Top dominators
        List<DominatorSummaryEntry> topDominators = snapshot.getTopRetainedObjects(20).stream()
                .map(obj -> {
                    DominatorNode node = snapshot.getDominatorNode(obj.getObjectId());
                    int childCount = node != null ? node.getDominatedChildren().size() : 0;
                    double percentage = ((double) obj.getRetainedSize() / totalHeapSize) * 100.0;
                    return DominatorSummaryEntry.builder()
                            .objectId(obj.getObjectId())
                            .className(obj.getClassName())
                            .shallowSizeBytes(obj.getShallowSize())
                            .retainedSizeBytes(obj.getRetainedSize())
                            .retainedPercentage(Math.round(percentage * 100.0) / 100.0)
                            .dominatedChildCount(childCount)
                            .build();
                })
                .collect(Collectors.toList());

        return HeapAnalysisSummaryResponse.builder()
                .snapshotId(snapshot.getSnapshotId())
                .headerVersion(snapshot.getHeaderVersion())
                .idSize(snapshot.getIdSize())
                .dumpTimestamp(snapshot.getDumpTimestamp())
                .totalHeapSizeBytes(snapshot.getTotalHeapSize())
                .totalObjectsCount(snapshot.getObjects().size())
                .totalClassesCount(snapshot.getClasses().size())
                .gcRootsCount(snapshot.getGcRoots().size())
                .classHistogram(histogram)
                .topDominators(topDominators)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RetainedSizeResponse getObjectRetainedSize(String snapshotId, long objectId) {
        HeapSnapshot snapshot = getSnapshot(snapshotId);
        HeapObject obj = snapshot.getObject(objectId);
        if (obj == null) {
            throw new ResourceNotFoundException("Heap object not found with ID: 0x" + Long.toHexString(objectId));
        }

        DominatorNode node = snapshot.getDominatorNode(objectId);
        int childCount = node != null ? node.getDominatedChildren().size() : 0;
        long immDomId = node != null ? node.getImmediateDominatorId() : 0;

        long totalHeap = Math.max(snapshot.getTotalHeapSize(), 1L);
        double percentage = ((double) obj.getRetainedSize() / totalHeap) * 100.0;

        return RetainedSizeResponse.builder()
                .objectId(objectId)
                .className(obj.getClassName())
                .shallowSizeBytes(obj.getShallowSize())
                .retainedSizeBytes(obj.getRetainedSize())
                .percentageOfTotalHeap(Math.round(percentage * 100.0) / 100.0)
                .dominatedObjectCount(childCount)
                .immediateDominatorId(immDomId)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReferenceChainResponse getReferenceChain(String snapshotId, long objectId) {
        HeapSnapshot snapshot = getSnapshot(snapshotId);
        HeapObject target = snapshot.getObject(objectId);
        if (target == null) {
            throw new ResourceNotFoundException("Heap object not found with ID: 0x" + Long.toHexString(objectId));
        }

        ReferenceChain chain = chainFinder.findShortestReferenceChain(snapshot, objectId)
                .orElse(ReferenceChain.builder()
                        .targetObjectId(objectId)
                        .targetClassName(target.getClassName())
                        .rootObjectId(0)
                        .rootType(null)
                        .hops(Collections.emptyList())
                        .length(0)
                        .build());

        List<HopDto> hopDtos = chain.getHops().stream()
                .map(h -> HopDto.builder()
                        .fromObjectId(h.getFromObjectId())
                        .fromClassName(h.getFromClassName())
                        .toObjectId(h.getToObjectId())
                        .toClassName(h.getToClassName())
                        .fieldName(h.getFieldName())
                        .build())
                .collect(Collectors.toList());

        return ReferenceChainResponse.builder()
                .targetObjectId(chain.getTargetObjectId())
                .targetClassName(chain.getTargetClassName())
                .rootObjectId(chain.getRootObjectId())
                .rootType(chain.getRootType() != null ? chain.getRootType().name() : "UNREACHABLE")
                .rootContext(chain.getRootContext())
                .hopsCount(chain.getLength())
                .hops(hopDtos)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DominatorNode> getDominatorTree(String snapshotId, int limit) {
        HeapSnapshot snapshot = getSnapshot(snapshotId);
        int max = limit > 0 ? limit : 50;
        return snapshot.getDominatorTree().values().stream()
                .sorted(Comparator.comparingLong(DominatorNode::getRetainedSize).reversed())
                .limit(max)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HeapSnapshot getSnapshot(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("Snapshot ID must not be blank");
        }
        HeapSnapshot snapshot = snapshotCache.get(snapshotId);
        if (snapshot == null) {
            throw new ResourceNotFoundException("Heap snapshot not found with ID: " + snapshotId);
        }
        return snapshot;
    }

    private static class ClassStats {
        int count = 0;
        long shallowSize = 0;
        long retainedSize = 0;

        void add(long shallow, long retained) {
            count++;
            shallowSize += shallow;
            retainedSize += retained;
        }
    }
}
