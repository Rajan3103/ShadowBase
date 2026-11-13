package com.shadowbase.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shadowbase.backend.Entity.Sandbox;

@Repository
public interface SandboxRepository extends JpaRepository<Sandbox, UUID> {

Optional<Sandbox> findById(UUID id);
List<Sandbox> findByName(String name);
}
