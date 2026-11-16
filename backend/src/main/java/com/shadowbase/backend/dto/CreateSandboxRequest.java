package com.shadowbase.backend.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for creating a new Sandbox environment.
 * <p>
 * Contains configuration for the sandbox name, owner user,
 * and initial containers to provision upon sandbox creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSandboxRequest {

    /**
     * Logical name of the new sandbox environment.
     */
    private String name;

    /**
     * Unique identifier of the user who owns this sandbox.
     */
    private UUID userId;

    /**
     * Optional list of container creation requests to automatically provision
     * during the initial sandbox creation workflow.
     */
    @Builder.Default
    private List<CreateContainerRequest> initialContainers = new ArrayList<>();
}
