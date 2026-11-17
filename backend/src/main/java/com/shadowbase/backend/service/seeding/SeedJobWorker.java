package com.shadowbase.backend.service.seeding;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.shadowbase.backend.Entity.SeedJob;
import com.shadowbase.backend.Entity.SeedJobStatus;
import com.shadowbase.backend.Entity.SeedTableProgress;
import com.shadowbase.backend.Entity.SeedTableStatus;
import com.shadowbase.backend.Repository.SeedJobRepository;
import com.shadowbase.backend.Repository.SeedTableProgressRepository;
import com.shadowbase.backend.service.ContainerConnectionProvider;
import com.shadowbase.backend.service.adapter.DatabaseDialectAdapter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Background worker responsible for asynchronous execution, progress persistence,
 * and restart recovery of persistent {@link SeedJob} runs.
 */
@Slf4j
@Component
public class SeedJobWorker {

    private final SeedJobRepository seedJobRepository;
    private final SeedTableProgressRepository tableProgressRepository;
    private final SourceConnectionProvider sourceConnectionProvider;
    private final ContainerConnectionProvider containerConnectionProvider;
    private final SchemaInspector schemaInspector;
    private final DataTransferEngine dataTransferEngine;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public SeedJobWorker(SeedJobRepository seedJobRepository,
                         SeedTableProgressRepository tableProgressRepository,
                         SourceConnectionProvider sourceConnectionProvider,
                         ContainerConnectionProvider containerConnectionProvider,
                         SchemaInspector schemaInspector,
                         DataTransferEngine dataTransferEngine) {
        this.seedJobRepository = seedJobRepository;
        this.tableProgressRepository = tableProgressRepository;
        this.sourceConnectionProvider = sourceConnectionProvider;
        this.containerConnectionProvider = containerConnectionProvider;
        this.schemaInspector = schemaInspector;
        this.dataTransferEngine = dataTransferEngine;
    }

    /**
     * Startup reconciliation: detects jobs left in RUNNING, VALIDATING, or CANCELLING
     * status across application restarts and transitions them to INTERRUPTED.
     */
    @PostConstruct
    public void recoverInterruptedJobs() {
        List<SeedJob> interrupted = seedJobRepository.findByStatusIn(List.of(
                SeedJobStatus.RUNNING,
                SeedJobStatus.VALIDATING,
                SeedJobStatus.CANCELLING
        ));

        for (SeedJob job : interrupted) {
            log.warn("Recovering orphaned seed job {} left in status {}. Marking as INTERRUPTED.",
                    job.getId(), job.getStatus());
            job.setStatus(SeedJobStatus.INTERRUPTED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage("Job execution was interrupted by server restart or unexpected shutdown.");
            seedJobRepository.save(job);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }

    /**
     * Submits a newly created, validated seed job for asynchronous background execution.
     *
     * @param seedJobId     unique identifier of the persistent SeedJob
     * @param orderedTables topologically sorted list of tables to transfer
     * @param adapter       database dialect adapter
     */
    public void submitJob(UUID seedJobId, List<String> orderedTables, DatabaseDialectAdapter adapter) {
        executorService.submit(() -> executeJob(seedJobId, orderedTables, adapter));
    }

    private void executeJob(UUID seedJobId, List<String> orderedTables, DatabaseDialectAdapter adapter) {
        SeedJob job = seedJobRepository.findById(seedJobId).orElse(null);
        if (job == null) {
            log.error("SeedJob {} not found for execution", seedJobId);
            return;
        }

        log.info("Worker claimed SeedJob {} for sandbox {}", job.getId(), job.getSandbox().getId());
        job.setStatus(SeedJobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        seedJobRepository.save(job);

        Connection sourceConn = null;
        Connection targetConn = null;

        try {
            sourceConn = sourceConnectionProvider.openSourceConnection(
                    job.getSourceDataSource(),
                    adapter.getDatabaseType()
            );
            adapter.configureSourceConnection(sourceConn, job.getConsistencyMode());

            targetConn = containerConnectionProvider.openConnection(job.getContainer().getId());

            long totalRead = 0;
            long totalInserted = 0;
            int completedTables = 0;

            for (String tableName : orderedTables) {
                // Check if user requested cancellation
                SeedJob currentJobState = seedJobRepository.findById(seedJobId).orElse(job);
                if (currentJobState.getStatus() == SeedJobStatus.CANCELLING) {
                    log.info("SeedJob {} cancelled by user. Halting remaining tables.", seedJobId);
                    markRemainingTablesSkipped(seedJobId, orderedTables, tableName);
                    currentJobState.setStatus(SeedJobStatus.CANCELLED);
                    currentJobState.setCompletedAt(LocalDateTime.now());
                    seedJobRepository.save(currentJobState);
                    return;
                }

                SeedTableProgress progress = tableProgressRepository
                        .findBySeedJobIdAndTableName(seedJobId, tableName)
                        .orElseGet(() -> SeedTableProgress.builder()
                                .seedJob(job)
                                .tableName(tableName)
                                .status(SeedTableStatus.PENDING)
                                .build());

                String pkCol = schemaInspector.getPrimaryKeyColumn(targetConn, tableName);

                dataTransferEngine.transferTable(
                        sourceConn,
                        targetConn,
                        tableName,
                        adapter,
                        pkCol,
                        progress,
                        () -> isJobCancelled(seedJobId),
                        tableProgressRepository::save
                );

                totalRead += (progress.getRowsRead() != null ? progress.getRowsRead() : 0);
                totalInserted += (progress.getRowsInserted() != null ? progress.getRowsInserted() : 0);
                completedTables++;

                job.setTotalRowsRead(totalRead);
                job.setTotalRowsInserted(totalInserted);
                job.setCompletedTables(completedTables);
                seedJobRepository.save(job);
            }

            job.setStatus(SeedJobStatus.SUCCESS);
            job.setCompletedAt(LocalDateTime.now());
            seedJobRepository.save(job);
            log.info("SeedJob {} finished successfully. Transferred {} rows across {} tables.",
                    seedJobId, totalInserted, completedTables);

        } catch (Exception e) {
            log.error("SeedJob {} failed: {}", seedJobId, e.getMessage(), e);
            SeedJob currentJobState = seedJobRepository.findById(seedJobId).orElse(job);

            if (currentJobState.getStatus() == SeedJobStatus.CANCELLING) {
                currentJobState.setStatus(SeedJobStatus.CANCELLED);
            } else if (currentJobState.getCompletedTables() > 0) {
                currentJobState.setStatus(SeedJobStatus.PARTIAL_FAILURE);
            } else {
                currentJobState.setStatus(SeedJobStatus.FAILED);
            }

            currentJobState.setErrorMessage(e.getMessage());
            currentJobState.setCompletedAt(LocalDateTime.now());
            seedJobRepository.save(currentJobState);

        } finally {
            closeQuietly(sourceConn);
            closeQuietly(targetConn);
        }
    }

    private boolean isJobCancelled(UUID seedJobId) {
        return seedJobRepository.findById(seedJobId)
                .map(j -> j.getStatus() == SeedJobStatus.CANCELLING || j.getStatus() == SeedJobStatus.CANCELLED)
                .orElse(false);
    }

    private void markRemainingTablesSkipped(UUID seedJobId, List<String> allTables, String currentTable) {
        boolean skip = false;
        for (String table : allTables) {
            if (table.equalsIgnoreCase(currentTable)) {
                skip = true;
                continue;
            }
            if (skip) {
                tableProgressRepository.findBySeedJobIdAndTableName(seedJobId, table).ifPresent(p -> {
                    if (p.getStatus() == SeedTableStatus.PENDING) {
                        p.setStatus(SeedTableStatus.SKIPPED);
                        p.setErrorMessage("Skipped due to job cancellation");
                        tableProgressRepository.save(p);
                    }
                });
            }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
        }
    }
}
