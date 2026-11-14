package com.shadowbase.backend.Service;

import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.Dto.ContainerResponse;
import com.shadowbase.backend.Dto.CreateContainerRequest;
import com.shadowbase.backend.Dto.CreateSandboxRequest;
import com.shadowbase.backend.Dto.SandboxResponse;
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
