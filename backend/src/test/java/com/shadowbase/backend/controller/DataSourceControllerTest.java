package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.shadowbase.backend.dto.CreateDataSourceRequest;
import com.shadowbase.backend.dto.DataSourceResponse;
import com.shadowbase.backend.service.DataSourceService;

@ExtendWith(MockitoExtension.class)
class DataSourceControllerTest {

    @Mock
    private DataSourceService dataSourceService;

    private DataSourceController controller;

    @BeforeEach
    void setUp() {
        controller = new DataSourceController(dataSourceService);
    }

    @Test
    void testListDataSources_Returns200Ok() {
        DataSourceResponse ds = DataSourceResponse.builder().id(UUID.randomUUID()).connectionName("ds1").build();
        when(dataSourceService.listDataSources()).thenReturn(List.of(ds));

        ResponseEntity<List<DataSourceResponse>> response = controller.listDataSources();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void testGetDataSource_Returns200Ok() {
        UUID id = UUID.randomUUID();
        DataSourceResponse ds = DataSourceResponse.builder().id(id).connectionName("ds1").build();
        when(dataSourceService.getDataSource(id)).thenReturn(ds);

        ResponseEntity<DataSourceResponse> response = controller.getDataSource(id);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(id, response.getBody().getId());
    }

    @Test
    void testCreateDataSource_Returns201Created() {
        CreateDataSourceRequest req = CreateDataSourceRequest.builder().connectionName("ds1").build();
        DataSourceResponse ds = DataSourceResponse.builder().id(UUID.randomUUID()).connectionName("ds1").build();
        when(dataSourceService.createDataSource(req)).thenReturn(ds);

        ResponseEntity<DataSourceResponse> response = controller.createDataSource(req);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("ds1", response.getBody().getConnectionName());
    }

    @Test
    void testLinkDataSource_Returns200Ok() {
        UUID sandboxId = UUID.randomUUID();
        String containerId = "c-100";
        UUID dsId = UUID.randomUUID();

        DataSourceResponse ds = DataSourceResponse.builder().id(dsId).containerId(containerId).build();
        when(dataSourceService.linkToContainer(sandboxId, containerId, dsId)).thenReturn(ds);

        ResponseEntity<DataSourceResponse> response = controller.linkDataSource(sandboxId, containerId, dsId);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(containerId, response.getBody().getContainerId());
    }

    @Test
    void testDeleteDataSource_Returns204NoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deleteDataSource(id);

        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(dataSourceService).deleteDataSource(id);
    }
}
