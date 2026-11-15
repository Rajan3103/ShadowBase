package com.shadowbase.backend.Repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shadowbase.backend.Entity.DataSource;

/**
 * Spring Data JPA repository for {@link DataSource} configurations.
 * Keyed by UUID.
 */
@Repository
public interface DatasourceRepository extends JpaRepository<DataSource, UUID> {

    /**
     * Finds the DataSource linked to a specific container ID.
     *
     * @param containerId Docker container ID
     * @return Optional containing the DataSource if found
     */
    Optional<DataSource> findByContainer_Id(String containerId);

}
