package com.shadowbase.backend.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.Entity.Container;
import com.shadowbase.backend.Entity.Container.ContainerStatus;
import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Entity.Sandbox;
import com.shadowbase.backend.Entity.Sandbox.SandboxStatus;
import com.shadowbase.backend.Exception.ResourceNotFoundException;
import com.shadowbase.backend.Exception.SandboxOperationException;
import com.shadowbase.backend.Exception.SqlQueryException;
import com.shadowbase.backend.Repository.ContainerRepository;
import com.shadowbase.backend.Repository.SandboxRepository;
import com.shadowbase.backend.dto.SqlQueryRequest;
import com.shadowbase.backend.dto.SqlQueryResponse;

@ExtendWith(MockitoExtension.class)
class SqlQueryServiceTest {

    @Mock
    private SandboxRepository sandboxRepository;

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private ContainerConnectionProvider connectionProvider;

    private SqlQueryServiceImpl sqlQueryService;
    private UUID sandboxId;
    private Sandbox testSandbox;
    private Container testContainer;

    @BeforeEach
    void setUp() {
        sqlQueryService = new SqlQueryServiceImpl(sandboxRepository, containerRepository, connectionProvider);
        sandboxId = UUID.randomUUID();

        testSandbox = Sandbox.builder()
                .id(sandboxId)
                .name("test-sandbox")
                .status(SandboxStatus.READY)
                .build();

        testContainer = Container.builder()
                .id("c1-docker-id")
                .sandbox(testSandbox)
                .databaseType(DatabaseType.POSTGRESQL)
                .status(ContainerStatus.RUNNING)
                .jdbcUrl("jdbc:postgresql://localhost:5432/test")
                .username("postgres")
                .password("postgres")
                .build();
    }

    @Test
    void testExecuteQuery_SelectQuery_ReturnsColumnsAndRows() throws Exception {
        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(testSandbox));
        when(containerRepository.findBySandboxIdAndStatus(sandboxId, ContainerStatus.RUNNING))
                .thenReturn(List.of(testContainer));

        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        ResultSet mockResultSet = mock(ResultSet.class);
        ResultSetMetaData mockMetaData = mock(ResultSetMetaData.class);

        when(connectionProvider.openConnection(testContainer)).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute("SELECT * FROM users;")).thenReturn(true);
        when(mockStatement.getResultSet()).thenReturn(mockResultSet);
        when(mockResultSet.getMetaData()).thenReturn(mockMetaData);

        when(mockMetaData.getColumnCount()).thenReturn(2);
        when(mockMetaData.getColumnLabel(1)).thenReturn("id");
        when(mockMetaData.getColumnLabel(2)).thenReturn("name");

        when(mockResultSet.next()).thenReturn(true, true, false);
        when(mockResultSet.getObject(1)).thenReturn(1, 2);
        when(mockResultSet.getObject(2)).thenReturn("Alice", "Bob");

        SqlQueryRequest request = SqlQueryRequest.builder()
                .sql("SELECT * FROM users;")
                .build();

        SqlQueryResponse response = sqlQueryService.executeQuery(sandboxId, request);

        assertNotNull(response);
        assertTrue(response.isHasResultSet());
        assertEquals(2, response.getColumns().size());
        assertEquals("id", response.getColumns().get(0));
        assertEquals("name", response.getColumns().get(1));
        assertEquals(2, response.getRows().size());
        assertEquals("1", response.getRows().get(0).get(0));
        assertEquals("Alice", response.getRows().get(0).get(1));
        assertEquals(2, response.getRowCount());
    }

    @Test
    void testExecuteQuery_DmlUpdateQuery_ReturnsAffectedRows() throws Exception {
        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(testSandbox));
        when(containerRepository.findBySandboxIdAndStatus(sandboxId, ContainerStatus.RUNNING))
                .thenReturn(List.of(testContainer));

        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);

        when(connectionProvider.openConnection(testContainer)).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute("UPDATE users SET name = 'Charlie';")).thenReturn(false);
        when(mockStatement.getUpdateCount()).thenReturn(5);

        SqlQueryRequest request = SqlQueryRequest.builder()
                .sql("UPDATE users SET name = 'Charlie';")
                .build();

        SqlQueryResponse response = sqlQueryService.executeQuery(sandboxId, request);

        assertNotNull(response);
        assertFalse(response.isHasResultSet());
        assertEquals(List.of("affected_rows"), response.getColumns());
        assertEquals(5, response.getRowCount());
        assertEquals(5, response.getRows().get(0).get(0));
    }

    @Test
    void testExecuteQuery_BlankSql_ThrowsIllegalArgumentException() {
        SqlQueryRequest request = SqlQueryRequest.builder().sql("   ").build();
        assertThrows(IllegalArgumentException.class, () -> sqlQueryService.executeQuery(sandboxId, request));
    }

    @Test
    void testExecuteQuery_SandboxNotFound_ThrowsResourceNotFoundException() {
        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.empty());
        SqlQueryRequest request = SqlQueryRequest.builder().sql("SELECT 1;").build();
        assertThrows(ResourceNotFoundException.class, () -> sqlQueryService.executeQuery(sandboxId, request));
    }

    @Test
    void testExecuteQuery_DestroyedSandbox_ThrowsSandboxOperationException() {
        testSandbox.setStatus(SandboxStatus.DESTROYED);
        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(testSandbox));

        SqlQueryRequest request = SqlQueryRequest.builder().sql("SELECT 1;").build();
        assertThrows(SandboxOperationException.class, () -> sqlQueryService.executeQuery(sandboxId, request));
    }

    @Test
    void testExecuteQuery_NoRunningContainers_ThrowsSandboxOperationException() {
        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(testSandbox));
        when(containerRepository.findBySandboxIdAndStatus(sandboxId, ContainerStatus.RUNNING))
                .thenReturn(Collections.emptyList());

        SqlQueryRequest request = SqlQueryRequest.builder().sql("SELECT 1;").build();
        assertThrows(SandboxOperationException.class, () -> sqlQueryService.executeQuery(sandboxId, request));
    }

    @Test
    void testExecuteQuery_SqlError_ThrowsSqlQueryException() throws Exception {
        when(sandboxRepository.findById(sandboxId)).thenReturn(Optional.of(testSandbox));
        when(containerRepository.findBySandboxIdAndStatus(sandboxId, ContainerStatus.RUNNING))
                .thenReturn(List.of(testContainer));

        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);

        when(connectionProvider.openConnection(testContainer)).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.execute(anyString())).thenThrow(new SQLException("relation 'unknown' does not exist"));

        SqlQueryRequest request = SqlQueryRequest.builder().sql("SELECT * FROM unknown;").build();
        assertThrows(SqlQueryException.class, () -> sqlQueryService.executeQuery(sandboxId, request));
    }
}
