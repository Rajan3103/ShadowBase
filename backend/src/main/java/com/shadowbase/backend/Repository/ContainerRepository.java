package com.shadowbase.backend.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;

/**
 * Spring Data JPA repository for {@link Container} entities.
 * Keyed by the Docker container ID (String).
 */
@Repository
public interface ContainerRepository extends JpaRepository<Container, String> {

    /**
     * Finds a container by its unique Docker container ID.
     *
     * @param id Docker container ID
     * @return Optional containing the container if found
     */
    Optional<Container> findById(String id);

    /**
     * Retrieves all containers belonging to a specific sandbox.
     *
     * @param sandboxId UUID of the parent sandbox
     * @return list of containers
     */
    List<Container> findBySandboxId(UUID sandboxId);

    /**
     * Retrieves all containers belonging to a specific sandbox matching a lifecycle status.
     *
     * @param sandboxId UUID of the parent sandbox
     * @param status    ContainerStatus (e.g. RUNNING)
     * @return list of matching containers
     */
    List<Container> findBySandboxIdAndStatus(UUID sandboxId, ContainerStatus status);

    /**
     * Retrieves a container by both its container ID and its parent sandbox UUID.
     *
     * @param id        Docker container ID
     * @param sandboxId UUID of the parent sandbox
     * @return Optional containing the container if found
     */
    Optional<Container> findByIdAndSandboxId(String id, UUID sandboxId);

    /**
     * Finds all containers matching a specific lifecycle status.
     *
     * @param status ContainerStatus (e.g. RUNNING, STOPPED, FAILED)
     * @return list of matching containers
     */
    List<Container> findByStatus(ContainerStatus status);

    /**
     * Finds all containers whose status is in the provided collection.
     *
     * @param statuses collection of ContainerStatus values
     * @return list of matching containers
     */
    List<Container> findByStatusIn(Collection<ContainerStatus> statuses);

    /**
     * Finds containers matching a database type and a collection of lifecycle statuses.
     * Useful for startup reconciliation against the Docker daemon.
     *
     * @param databaseType DatabaseType (MYSQL or POSTGRESQL)
     * @param statuses     collection of ContainerStatus values
     * @return list of matching containers
     */
    List<Container> findByDatabaseTypeAndStatusIn(
            DatabaseType databaseType,
            Collection<ContainerStatus> statuses
    );

    /**
     * Finds containers belonging to a specific sandbox and matching a database type.
     *
     * @param sandboxId    UUID of the parent sandbox
     * @param databaseType DatabaseType (MYSQL or POSTGRESQL)
     * @return list of matching containers
     */
    List<Container> findBySandboxIdAndDatabaseType(UUID sandboxId, DatabaseType databaseType);

    /**
     * Finds the single container associated with a specific DataSource.
     * Since each container hosts exactly one database and a DataSource is linked to only this container.
     *
     * @param dataSourceId UUID of the DataSource
     * @return Optional containing the Container if found
     */
    Optional<Container> findByDataSourceId(UUID dataSourceId);

    /**
     * Checks if a container is already linked to the specified DataSource.
     *
     * @param dataSourceId UUID of the DataSource
     * @return true if an associated container exists, false otherwise
     */
    boolean existsByDataSourceId(UUID dataSourceId);
}
