package com.shadowbase.backend.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shadowbase.backend.Entity.SeedJob;
import com.shadowbase.backend.Entity.SeedJobStatus;

/**
 * Spring Data JPA repository for managing persistent {@link SeedJob} entities.
 */
@Repository
public interface SeedJobRepository extends JpaRepository<SeedJob, UUID> {

    /**
     * Finds all seed jobs associated with a sandbox, ordered newest to oldest.
     *
     * @param sandboxId the sandbox identifier
     * @return list of seed jobs
     */
    List<SeedJob> findBySandboxIdOrderByCreatedAtDesc(UUID sandboxId);

    /**
     * Retrieves a seed job by its ID and associated sandbox ID for access control.
     *
     * @param id        the seed job ID
     * @param sandboxId the sandbox ID
     * @return Optional containing the job if found
     */
    Optional<SeedJob> findByIdAndSandboxId(UUID id, UUID sandboxId);

    /**
     * Claims the next pending job in QUEUED status in FIFO order.
     *
     * @param status the SeedJobStatus (typically QUEUED)
     * @return Optional containing the oldest queued job
     */
    Optional<SeedJob> findFirstByStatusOrderByCreatedAtAsc(SeedJobStatus status);

    /**
     * Finds all jobs matching any of the given statuses.
     * Useful on application startup for identifying INTERRUPTED jobs.
     *
     * @param statuses collection of statuses
     * @return list of matching jobs
     */
    List<SeedJob> findByStatusIn(Collection<SeedJobStatus> statuses);

    /**
     * Checks if there are any active (QUEUED, VALIDATING, RUNNING, CANCELLING) jobs
     * for a given sandbox to prevent duplicate concurrent runs.
     *
     * @param sandboxId the sandbox ID
     * @param statuses  collection of active statuses
     * @return true if an active job exists; false otherwise
     */
    boolean existsBySandboxIdAndStatusIn(UUID sandboxId, Collection<SeedJobStatus> statuses);

    /**
     * Checks if there are any active jobs targeting a specific database container.
     *
     * @param containerId the container ID
     * @param statuses    collection of active statuses
     * @return true if an active job exists; false otherwise
     */
    boolean existsByContainerIdAndStatusIn(String containerId, Collection<SeedJobStatus> statuses);
}
