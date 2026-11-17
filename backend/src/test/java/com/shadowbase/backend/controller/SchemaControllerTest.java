package com.shadowbase.backend.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.SchemaStatus;
import com.shadowbase.backend.dto.SchemaImportResponse;
import com.shadowbase.backend.service.SchemaService;

@ExtendWith(MockitoExtension.class)
class SchemaControllerTest {

    @Mock
    private SchemaService schemaService;

    @InjectMocks
    private SchemaController schemaController;

    private UUID sandboxId;
    private UUID importId;

    @BeforeEach
    void setUp() {
        sandboxId = UUID.randomUUID();
        importId = UUID.randomUUID();
    }

    @Test
    void testImportSchema_Success() {
        SchemaImportResponse response = SchemaImportResponse.builder()
                .id(importId)
                .sandboxId(sandboxId)
                .containerId("docker-c1")
                .fileName("schema.sql")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(SchemaStatus.SUCCESS)
                .startedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();

        when(schemaService.importSchema(eq(sandboxId), any(), any(), any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "schema.sql", "text/plain",
                "CREATE TABLE test (id INT);".getBytes()
        );

        ResponseEntity<SchemaImportResponse> result = schemaController.importSchema(sandboxId, null, null, file, null);

        assertNotNull(result);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(importId, result.getBody().getId());
        assertEquals(SchemaStatus.SUCCESS, result.getBody().getStatus());
        assertEquals("schema.sql", result.getBody().getFileName());

        verify(schemaService).importSchema(eq(sandboxId), eq(null), eq(file), eq(null));
    }

    @Test
    void testImportSchema_WithContainerId_Success() {
        String containerId = "docker-c1";
        SchemaImportResponse response = SchemaImportResponse.builder()
                .id(importId)
                .sandboxId(sandboxId)
                .containerId(containerId)
                .fileName("schema.sql")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(SchemaStatus.SUCCESS)
                .build();

        when(schemaService.importSchema(eq(sandboxId), eq(containerId), any(), any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "schema.sql", "text/plain",
                "CREATE TABLE test (id INT);".getBytes()
        );

        ResponseEntity<SchemaImportResponse> result = schemaController.importSchema(sandboxId, containerId, null, file, null);

        assertNotNull(result);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(containerId, result.getBody().getContainerId());
        verify(schemaService).importSchema(eq(sandboxId), eq(containerId), eq(file), eq(null));
    }

    @Test
    void testGetLatestStatus_Success() {
        SchemaImportResponse response = SchemaImportResponse.builder()
                .id(importId)
                .sandboxId(sandboxId)
                .containerId("docker-c1")
                .fileName("schema.sql")
                .databaseType(DatabaseType.POSTGRESQL)
                .status(SchemaStatus.SUCCESS)
                .build();

        when(schemaService.getLatestImportStatus(eq(sandboxId), any())).thenReturn(response);

        ResponseEntity<SchemaImportResponse> result = schemaController.getLatestImportStatus(sandboxId, null, null);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(importId, result.getBody().getId());
        assertEquals(SchemaStatus.SUCCESS, result.getBody().getStatus());
    }

    @Test
    void testGetLatestStatus_NoContent() {
        when(schemaService.getLatestImportStatus(eq(sandboxId), any())).thenReturn(null);

        ResponseEntity<SchemaImportResponse> result = schemaController.getLatestImportStatus(sandboxId, null, null);

        assertNotNull(result);
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    }

    @Test
    void testGetImportHistory_Success() {
        SchemaImportResponse response = SchemaImportResponse.builder()
                .id(importId)
                .sandboxId(sandboxId)
                .status(SchemaStatus.SUCCESS)
                .fileName("init.sql")
                .build();

        when(schemaService.getImportHistory(eq(sandboxId), any())).thenReturn(List.of(response));

        ResponseEntity<List<SchemaImportResponse>> result = schemaController.getImportHistory(sandboxId, null, null);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().size());
        assertEquals("init.sql", result.getBody().get(0).getFileName());
    }
}
