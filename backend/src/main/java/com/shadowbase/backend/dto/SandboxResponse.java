package com.shadowbase.backend.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Entity.SchemaStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) representing a Sandbox environment response.
 * <p>
 * Contains complete details about the sandbox, its aggregated status,
 * schema initialization status, and active container summaries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxResponse {

    /**
     * Unique identifier of the sandbox.
     */
    private UUID id;

    /**
     * Human-readable name of the sandbox.
     */
    private String name;

    /**
     * Unique identifier of the user who owns this sandbox.
     */
    private UUID userId;

    /**
     * Current aggregated operational status of the sandbox.
     */
    private SandboxStatus status;

    /**
     * Current schema initialization status of the sandbox.
     */
    private SchemaStatus schemaStatus;

    /**
     * List of provisioned database containers running within or belonging to this sandbox.
     */
    @Builder.Default
    private List<ContainerResponse> containers = new ArrayList<>();

    /**
     * Timestamp when the sandbox was created.
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when the sandbox was last updated.
     */
    private LocalDateTime updatedAt;
}
