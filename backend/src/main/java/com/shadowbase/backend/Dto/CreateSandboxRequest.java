package com.shadowbase.backend.Dto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSandboxRequest {

    private String name;

    private UUID userId;

    @Builder.Default
    private List<UUID> dataSourceIds = new ArrayList<>();

    @Builder.Default
    private List<CreateContainerRequest> initialContainers = new ArrayList<>();

    public void setDataSourceId(UUID dataSourceId) {
        if (dataSourceId != null) {
            if (this.dataSourceIds == null) {
                this.dataSourceIds = new ArrayList<>();
            }
            this.dataSourceIds.add(dataSourceId);
        }
    }
}
