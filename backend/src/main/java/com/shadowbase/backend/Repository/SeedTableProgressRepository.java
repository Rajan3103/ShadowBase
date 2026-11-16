package com.shadowbase.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shadowbase.backend.Entity.SeedTableProgress;

/**
 * Spring Data JPA repository for {@link SeedTableProgress} records.
 */
@Repository
public interface SeedTableProgressRepository extends JpaRepository<SeedTableProgress, UUID> {

    /**
     * Retrieves all table progress entries for a given job ordered by execution start.
     *
     * @param seedJobId the seed job ID
     * @return list of table progress records
     */
    List<SeedTableProgress> findBySeedJobIdOrderByStartedAtAsc(UUID seedJobId);

    /**
     * Finds progress for a specific table within a seed job.
     *
     * @param seedJobId the seed job ID
     * @param tableName table name
     * @return Optional containing table progress if found
     */
    Optional<SeedTableProgress> findBySeedJobIdAndTableName(UUID seedJobId, String tableName);
}
