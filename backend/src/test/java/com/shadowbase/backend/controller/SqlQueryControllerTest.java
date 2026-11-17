package com.shadowbase.backend.controller;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.shadowbase.backend.dto.SqlQueryRequest;
import com.shadowbase.backend.dto.SqlQueryResponse;
import com.shadowbase.backend.service.SqlQueryService;

@ExtendWith(MockitoExtension.class)
class SqlQueryControllerTest {

    @Mock
    private SqlQueryService sqlQueryService;

    private SqlQueryController controller;
    private UUID sandboxId;

    @BeforeEach
    void setUp() {
        controller = new SqlQueryController(sqlQueryService);
        sandboxId = UUID.randomUUID();
    }

    @Test
    void testExecuteSandboxQuery_Returns200Ok() {
        SqlQueryRequest request = SqlQueryRequest.builder().sql("SELECT 1;").build();
        SqlQueryResponse mockResponse = SqlQueryResponse.builder()
                .columns(List.of("1"))
                .rows(List.of(List.of(1)))
                .rowCount(1)
                .hasResultSet(true)
                .build();

        when(sqlQueryService.executeQuery(eq(sandboxId), any(SqlQueryRequest.class))).thenReturn(mockResponse);

        ResponseEntity<SqlQueryResponse> response = controller.executeSandboxQuery(sandboxId, request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getRowCount());
        verify(sqlQueryService).executeQuery(sandboxId, request);
    }

    @Test
    void testExecuteContainerQuery_SetsContainerId_Returns200Ok() {
        SqlQueryRequest request = SqlQueryRequest.builder().sql("SELECT 1;").build();
        String containerId = "c123";
        SqlQueryResponse mockResponse = SqlQueryResponse.builder()
                .columns(List.of("1"))
                .rows(List.of(List.of(1)))
                .rowCount(1)
                .hasResultSet(true)
                .build();

        when(sqlQueryService.executeQuery(eq(sandboxId), any(SqlQueryRequest.class))).thenReturn(mockResponse);

        ResponseEntity<SqlQueryResponse> response = controller.executeContainerQuery(sandboxId, containerId, request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(containerId, request.getContainerId());
    }
}
