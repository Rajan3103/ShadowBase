package com.shadowbase.backend.service;

import java.util.List;
import java.util.UUID;

import com.shadowbase.backend.dto.CreateDataSourceRequest;
import com.shadowbase.backend.dto.DataSourceResponse;
import com.shadowbase.backend.dto.DataSourceTestResponse;

/**
 * Service managing external source database configurations and their bindings
 * to sandbox database containers for schema migration and data seeding.
 */
public interface DataSourceService {

    /**
     * Lists all registered data source configurations.
     *
     * @return list of DataSourceResponse objects
     */
    List<DataSourceResponse> listDataSources();

    /**
     * Retrieves data source details by ID.
     *
     * @param id UUID of the DataSource
     * @return DataSourceResponse
     */
    DataSourceResponse getDataSource(UUID id);

    /**
     * Registers a new external data source configuration.
     *
     * @param request configuration parameters
     * @return provisioned DataSourceResponse
     */
    DataSourceResponse createDataSource(CreateDataSourceRequest request);

    /**
     * Links an existing DataSource to a specific sandbox database container.
     *
     * @param sandboxId    UUID of the parent sandbox
     * @param containerId  Docker container ID
     * @param dataSourceId UUID of the DataSource to link
     * @return updated DataSourceResponse
     */
    DataSourceResponse linkToContainer(UUID sandboxId, String containerId, UUID dataSourceId);

    /**
     * Creates a new DataSource and immediately links it to a container in one step.
     *
     * @param sandboxId   UUID of the parent sandbox
     * @param containerId Docker container ID
     * @param request     DataSource creation payload
     * @return created and linked DataSourceResponse
     */
    DataSourceResponse createAndLinkToContainer(UUID sandboxId, String containerId, CreateDataSourceRequest request);

    /**
     * Deletes a data source configuration. Unlinks from any container if bound.
     *
     * @param id UUID of the DataSource to delete
     */
    void deleteDataSource(UUID id);

    /**
     * Tests connectivity, credentials, and response latency to an existing DataSource.
     *
     * @param id UUID of the DataSource
     * @return DataSourceTestResponse with connection status and latency
     */
    DataSourceTestResponse testConnection(UUID id);

    /**
     * Tests connectivity to a prospective DataSource configuration before saving.
     *
     * @param request creation request containing host, port, db name, and credentials
     * @return DataSourceTestResponse with connection status and latency
     */
    DataSourceTestResponse testConnection(CreateDataSourceRequest request);
}
