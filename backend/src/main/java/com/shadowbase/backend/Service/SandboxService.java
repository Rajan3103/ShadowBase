package com.shadowbase.backend.service;

import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.dto.ContainerResponse;
import com.shadowbase.backend.dto.CreateContainerRequest;
import com.shadowbase.backend.dto.CreateSandboxRequest;
import com.shadowbase.backend.dto.SandboxResponse;
import com.shadowbase.backend.Entity.Sandbox;

public interface SandboxService {

    SandboxResponse createSandbox(CreateSandboxRequest request);

    SandboxResponse getSandbox(UUID sandboxId);

    List<SandboxResponse> listSandboxesByUser(UUID userId);

    SandboxResponse startSandbox(UUID sandboxId);

    SandboxResponse stopSandbox(UUID sandboxId);

    void deleteSandbox(UUID sandboxId);

    ContainerResponse addContainerToSandbox(UUID sandboxId, CreateContainerRequest request);

    SandboxResponse mapToResponse(Sandbox sandbox);
}
