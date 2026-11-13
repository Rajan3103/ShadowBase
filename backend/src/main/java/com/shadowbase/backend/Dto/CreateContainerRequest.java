package com.shadowbase.backend.Dto;

import java.util.UUID;

import com.shadowbase.backend.Entity.Container.DatabaseType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContainerRequest {

    private UUID sandboxId;

    private DatabaseType databaseType;

    private String databaseName;

    private String username;

    private String password;

    private String imageName;

    private String credentialReference;

    private UUID dataSourceId;
}
