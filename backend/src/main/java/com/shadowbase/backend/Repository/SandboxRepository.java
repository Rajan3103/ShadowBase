package com.shadowbase.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;

/**
 * Spring Data JPA repository for {@link Sandbox} entities.
 * Keyed by sandbox UUID.
 */
@Repository
public interface SandboxRepository extends JpaRepository<Sandbox, UUID> {

    /**
     * Finds a sandbox by its primary key UUID.
     *
     * @param id the sandbox UUID
     * @return Optional containing the Sandbox if found
     */
    Optional<Sandbox> findById(UUID id);

    /**
     * Finds sandboxes matching a specific name.
     *
     * @param name name of the sandbox
     * @return list of matching sandboxes
     */
    List<Sandbox> findByName(String name);

    /**
     * Finds all sandboxes belonging to a specific user.
     *
     * @param userId UUID of the owning user
     * @return list of matching sandboxes
     */
    List<Sandbox> findByUserId(UUID userId);

    /**
     * Finds sandboxes matching a specific lifecycle status.
     *
     * @param status SandboxStatus (e.g. CREATING, READY, DEGRADED, STOPPED, DESTROYED)
     * @return list of matching sandboxes
     */
    List<Sandbox> findByStatus(SandboxStatus status);
}
