package com.shadowbase.backend.service.seeding;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.Entity.SeedJob;
import com.shadowbase.backend.Entity.SeedJobStatus;
import com.shadowbase.backend.Repository.SeedJobRepository;
import com.shadowbase.backend.Repository.SeedTableProgressRepository;
import com.shadowbase.backend.service.ContainerConnectionProvider;

/**
 * Unit tests for {@link SeedJobWorker}.
 * Tests restart recovery of orphaned active jobs.
 */
@ExtendWith(MockitoExtension.class)
class SeedJobWorkerTest {

    @Mock
    private SeedJobRepository seedJobRepository;

    @Mock
    private SeedTableProgressRepository tableProgressRepository;

    @Mock
    private SourceConnectionProvider sourceConnectionProvider;

    @Mock
    private ContainerConnectionProvider containerConnectionProvider;

    @Mock
    private SchemaInspector schemaInspector;

    @Mock
    private DataTransferEngine dataTransferEngine;

    private SeedJobWorker worker;

    @BeforeEach
    void setUp() {
        worker = new SeedJobWorker(
                seedJobRepository,
                tableProgressRepository,
                sourceConnectionProvider,
                containerConnectionProvider,
                schemaInspector,
                dataTransferEngine
        );
    }

    @Test
    void testRecoverInterruptedJobs_MarksRunningJobsAsInterrupted() {
        SeedJob runningJob = SeedJob.builder()
                .id(UUID.randomUUID())
                .status(SeedJobStatus.RUNNING)
                .build();

        when(seedJobRepository.findByStatusIn(any())).thenReturn(List.of(runningJob));

        worker.recoverInterruptedJobs();

        assertEquals(SeedJobStatus.INTERRUPTED, runningJob.getStatus());
        assertNotNull(runningJob.getCompletedAt());
        assertNotNull(runningJob.getErrorMessage());
        verify(seedJobRepository).save(runningJob);
    }
}
