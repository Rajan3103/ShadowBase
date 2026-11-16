
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

@Repository
public interface ContainerRepository extends JpaRepository<Container, String> {

    Optional<Container> findById(String id);

    List<Container> findBySandboxId(UUID sandboxId);

    Optional<Container> findByIdAndSandboxId(String id, UUID sandboxId);

    List<Container> findByStatus(ContainerStatus status);

    List<Container> findByStatusIn(Collection<ContainerStatus> statuses);

    List<Container> findByDatabaseTypeAndStatusIn(
            DatabaseType databaseType,
            Collection<ContainerStatus> statuses
    );
}
