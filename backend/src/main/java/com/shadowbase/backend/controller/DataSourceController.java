package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shadowbase.backend.dto.CreateDataSourceRequest;
import com.shadowbase.backend.dto.DataSourceResponse;
import com.shadowbase.backend.dto.DataSourceTestResponse;
import com.shadowbase.backend.service.DataSourceService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller exposing management endpoints for external DataSources
 * and their container associations.
 * <p>
 * Base URI: {@code /api/datasources}
 */
@Slf4j
@RestController
@RequestMapping
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    /**
     * Lists all registered external DataSources.
     *
     * @return 200 OK with list of DataSourceResponse objects
     */
    @GetMapping("/api/datasources")
    public ResponseEntity<List<DataSourceResponse>> listDataSources() {
        return ResponseEntity.ok(dataSourceService.listDataSources());
    }

    /**
     * Retrieves DataSource configuration details by ID.
     *
     * @param id UUID of the DataSource
     * @return 200 OK with DataSourceResponse
     */
    @GetMapping("/api/datasources/{id}")
    public ResponseEntity<DataSourceResponse> getDataSource(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(dataSourceService.getDataSource(id));
    }

    /**
     * Registers a new DataSource configuration.
     *
     * @param request configuration details
     * @return 201 CREATED with the provisioned DataSourceResponse
     */
    @PostMapping("/api/datasources")
    public ResponseEntity<DataSourceResponse> createDataSource(@RequestBody CreateDataSourceRequest request) {
        log.info("REST request to create DataSource: {}", request != null ? request.getConnectionName() : "null");
        DataSourceResponse response = dataSourceService.createDataSource(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Deletes a DataSource configuration.
     *
     * @param id UUID of the DataSource
     * @return 204 NO_CONTENT
     */
    @DeleteMapping("/api/datasources/{id}")
    public ResponseEntity<Void> deleteDataSource(@PathVariable("id") UUID id) {
        log.info("REST request to delete DataSource: {}", id);
        dataSourceService.deleteDataSource(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Tests connectivity and credentials for an existing DataSource.
     *
     * @param id UUID of the DataSource
     * @return 200 OK with DataSourceTestResponse
     */
    @PostMapping("/api/datasources/{id}/test")
    public ResponseEntity<DataSourceTestResponse> testDataSourceConnection(@PathVariable("id") UUID id) {
        log.info("REST request to test DataSource connection: {}", id);
        DataSourceTestResponse response = dataSourceService.testConnection(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Tests prospective connectivity for a new DataSource configuration before saving.
     *
     * @param request configuration details
     * @return 200 OK with DataSourceTestResponse
     */
    @PostMapping("/api/datasources/test")
    public ResponseEntity<DataSourceTestResponse> testProspectiveConnection(
            @RequestBody CreateDataSourceRequest request) {

        log.info("REST request to test prospective DataSource connection: host={}",
                request != null ? request.getHost() : "null");
        DataSourceTestResponse response = dataSourceService.testConnection(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Links an existing DataSource to a specific sandbox database container.
     *
     * @param sandboxId    UUID of the sandbox
     * @param containerId  Docker container ID
     * @param dataSourceId UUID of the DataSource to link
     * @return 200 OK with linked DataSourceResponse
     */
    @PostMapping("/api/sandboxes/{sandboxId}/containers/{containerId}/datasource/{dataSourceId}")
    public ResponseEntity<DataSourceResponse> linkDataSource(
            @PathVariable("sandboxId") UUID sandboxId,
            @PathVariable("containerId") String containerId,
            @PathVariable("dataSourceId") UUID dataSourceId) {

        log.info("REST request to link DataSource {} to container {} in sandbox {}",
                dataSourceId, containerId, sandboxId);
        DataSourceResponse response = dataSourceService.linkToContainer(sandboxId, containerId, dataSourceId);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new DataSource and links it directly to a sandbox database container.
     *
     * @param sandboxId   UUID of the sandbox
     * @param containerId Docker container ID
     * @param request     creation parameters
     * @return 201 CREATED with created and linked DataSourceResponse
     */
    @PostMapping("/api/sandboxes/{sandboxId}/containers/{containerId}/datasource")
    public ResponseEntity<DataSourceResponse> createAndLinkDataSource(
            @PathVariable("sandboxId") UUID sandboxId,
            @PathVariable("containerId") String containerId,
            @RequestBody CreateDataSourceRequest request) {

        log.info("REST request to create and link DataSource to container {} in sandbox {}",
                containerId, sandboxId);
        DataSourceResponse response = dataSourceService.createAndLinkToContainer(sandboxId, containerId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
