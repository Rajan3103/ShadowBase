package com.shadowbase.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shadowbase.backend.Entity.SchemaImport;
import com.shadowbase.backend.Entity.SchemaStatus;

/**
 * Spring Data JPA repository for {@link SchemaImport} entities.
 * Keyed by UUID.
 */
@Repository
public interface SchemaImportRepository extends JpaRepository<SchemaImport, UUID> {

    /**
     * Retrieves the complete schema import history for a given sandbox, ordered chronologically newest first.
     *
     * @param sandboxId UUID of the sandbox
     * @return list of schema import records
     */
    List<SchemaImport> findBySandboxIdOrderByStartedAtDesc(UUID sandboxId);

    /**
     * Retrieves the most recent schema import attempt for a given sandbox.
     *
     * @param sandboxId UUID of the sandbox
     * @return Optional containing the latest schema import if one exists
     */
    Optional<SchemaImport> findFirstBySandboxIdOrderByStartedAtDesc(UUID sandboxId);

    /**
     * Checks if any schema import for the specified sandbox is currently in the given status
     * (e.g. to guard against concurrent IN_PROGRESS imports).
     *
     * @param sandboxId UUID of the sandbox
     * @param status    the status to check (e.g. IN_PROGRESS)
     * @return true if a matching record exists, false otherwise
     */
    boolean existsBySandboxIdAndStatus(UUID sandboxId, SchemaStatus status);

    /**
     * Retrieves the complete schema import history for a specific database container.
     *
     * @param containerId Docker container ID
     * @return list of schema import records
     */
    List<SchemaImport> findByContainerIdOrderByStartedAtDesc(String containerId);

    /**
     * Retrieves the most recent schema import attempt for a specific container.
     *
     * @param containerId Docker container ID
     * @return Optional containing the latest schema import if one exists
     */
    Optional<SchemaImport> findFirstByContainerIdOrderByStartedAtDesc(String containerId);

    /**
     * Checks if a schema import for a specific container is currently in the given status.
     *
     * @param containerId Docker container ID
     * @param status      the status to check (e.g. IN_PROGRESS)
     * @return true if matching record exists
     */
    boolean existsByContainerIdAndStatus(String containerId, SchemaStatus status);

    /**
     * Retrieves the most recent schema import for a specific container within a sandbox.
     *
     * @param sandboxId   UUID of the sandbox
     * @param containerId Docker container ID
     * @return Optional containing the latest schema import if one exists
     */
    Optional<SchemaImport> findFirstBySandboxIdAndContainerIdOrderByStartedAtDesc(UUID sandboxId, String containerId);

    /**
     * Retrieves history of schema imports for a specific container within a sandbox.
     *
     * @param sandboxId   UUID of the sandbox
     * @param containerId Docker container ID
     * @return list of matching records
     */
    List<SchemaImport> findBySandboxIdAndContainerIdOrderByStartedAtDesc(UUID sandboxId, String containerId);
}
