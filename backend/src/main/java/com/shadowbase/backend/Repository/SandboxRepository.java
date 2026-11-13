package com.shadowbase.backend.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shadowbase.backend.Entity.Sandbox;

public interface SandboxRepository extends JpaRepository{

Optional<Sandbox> findById(UUID id);
List<Sandbox> findByName(String name);
}
