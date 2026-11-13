package com.shadowbase.backend.Repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shadowbase.backend.Entity.DataSource;

@Repository
public interface DatasourceRepository extends JpaRepository<DataSource, UUID> {
    
}
