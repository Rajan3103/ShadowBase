package com.shadowbase.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shadowbase.backend.Entity.Container;

public interface ContainerRepository extends JpaRepository<Container,String> {

Optional<Container> findById(String id);
List<Container> findBySandboxId(UUID sandboxId);
}
