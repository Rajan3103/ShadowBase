package com.shadowbase.backend.Dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerResponse {

    private String id;

    private UUID sandboxId;

    private String imageName;

    private String host;

    private Integer port;

    private String jdbcUrl;

    private String username;

    private String databaseName;

    private DatabaseType databaseType;

    private ContainerStatus status;

    private String credentialReference;

    private UUID dataSourceId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime stoppedAt;
}
