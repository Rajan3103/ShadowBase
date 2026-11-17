package com.shadowbase.backend.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.DataSource;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.DatasourceRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.dto.CreateDataSourceRequest;
import com.shadowbase.backend.dto.DataSourceResponse;

@ExtendWith(MockitoExtension.class)
class DataSourceServiceTest {

    @Mock
    private DatasourceRepository datasourceRepository;

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private SandboxRepository sandboxRepository;

    private DataSourceServiceImpl dataSourceService;

    @BeforeEach
    void setUp() {
        dataSourceService = new DataSourceServiceImpl(datasourceRepository, containerRepository, sandboxRepository);
    }

    @Test
    void testListDataSources() {
        DataSource ds = DataSource.builder()
                .id(UUID.randomUUID())
                .connectionName("prod-db")
                .host("localhost")
                .port(5432)
                .databaseName("production")
                .databaseUsername("postgres")
                .build();

        when(datasourceRepository.findAll()).thenReturn(List.of(ds));

        List<DataSourceResponse> list = dataSourceService.listDataSources();

        assertEquals(1, list.size());
        assertEquals("prod-db", list.get(0).getConnectionName());
    }

    @Test
    void testGetDataSource_Success() {
        UUID id = UUID.randomUUID();
        DataSource ds = DataSource.builder()
                .id(id)
                .connectionName("prod-db")
                .host("localhost")
                .port(5432)
                .databaseName("production")
                .databaseUsername("postgres")
                .build();

        when(datasourceRepository.findById(id)).thenReturn(Optional.of(ds));

        DataSourceResponse res = dataSourceService.getDataSource(id);

        assertNotNull(res);
        assertEquals(id, res.getId());
    }

    @Test
    void testCreateDataSource_Success() {
        CreateDataSourceRequest req = CreateDataSourceRequest.builder()
                .connectionName("main-source")
                .host("db.company.com")
                .port(5432)
                .databaseName("app")
                .databaseUsername("reader")
                .databasePassword("secret123")
                .build();

        DataSource saved = DataSource.builder()
                .id(UUID.randomUUID())
                .connectionName("main-source")
                .host("db.company.com")
                .port(5432)
                .databaseName("app")
                .databaseUsername("reader")
                .databasePassword("secret123")
                .build();

        when(datasourceRepository.save(any(DataSource.class))).thenReturn(saved);

        DataSourceResponse res = dataSourceService.createDataSource(req);

        assertNotNull(res);
        assertEquals("main-source", res.getConnectionName());
        verify(datasourceRepository).save(any(DataSource.class));
    }

    @Test
    void testLinkToContainer_Success() {
        UUID sandboxId = UUID.randomUUID();
        String containerId = "c-100";
        UUID dsId = UUID.randomUUID();

        Sandbox sandbox = Sandbox.builder().id(sandboxId).build();
        Container container = Container.builder().id(containerId).sandbox(sandbox).build();
        DataSource ds = DataSource.builder().id(dsId).connectionName("source").build();

        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(sandbox));
        when(containerRepository.findById(containerId)).thenReturn(Optional.of(container));
        when(datasourceRepository.findById(dsId)).thenReturn(Optional.of(ds));

        DataSourceResponse res = dataSourceService.linkToContainer(sandboxId, containerId, dsId);

        assertNotNull(res);
        assertEquals(containerId, res.getContainerId());
        verify(containerRepository).save(container);
    }

    @Test
    void testDeleteDataSource_Success() {
        UUID id = UUID.randomUUID();
        DataSource ds = DataSource.builder().id(id).build();
        when(datasourceRepository.findById(id)).thenReturn(Optional.of(ds));
        when(containerRepository.findByDataSourceId(id)).thenReturn(Optional.empty());

        dataSourceService.deleteDataSource(id);

        verify(datasourceRepository).delete(ds);
    }

    @Test
    void testCreateDataSource_InvalidPort_ThrowsException() {
        CreateDataSourceRequest req = CreateDataSourceRequest.builder()
                .connectionName("source")
                .host("localhost")
                .port(70000)
                .databaseName("db")
                .databaseUsername("user")
                .build();

        assertThrows(IllegalArgumentException.class, () -> dataSourceService.createDataSource(req));
    }
}
