package com.shadowbase.backend.Dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxResponse {

    private UUID id;

    private String name;

    private UUID userId;

    @Builder.Default
    private List<UUID> dataSourceIds = new ArrayList<>();

    private SandboxStatus status;

    @Builder.Default
    private List<ContainerResponse> containers = new ArrayList<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
