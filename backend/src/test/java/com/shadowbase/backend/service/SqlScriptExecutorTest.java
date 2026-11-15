package com.shadowbase.backend.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Exception.SchemaImportException;

@ExtendWith(MockitoExtension.class)
class SqlScriptExecutorTest {

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    private SqlScriptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new SqlScriptExecutor();
    }

    @Test
    void testExecute_Success() throws SQLException {
        when(connection.createStatement()).thenReturn(statement);
        when(statement.getUpdateCount()).thenReturn(-1);

        String sql = "-- Sample schema\n" +
                "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(50));\n" +
                "INSERT INTO users VALUES (1, 'Alice');\n";

        ByteArrayInputStream in = new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8));
        executor.execute(connection, in, DatabaseType.POSTGRESQL);

        verify(connection).createStatement();
    }

    @Test
    void testExecute_EmptyScript_ThrowsException() {
        ByteArrayInputStream in = new ByteArrayInputStream("   \n  \t ".getBytes(StandardCharsets.UTF_8));

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> executor.execute(connection, in, DatabaseType.POSTGRESQL)
        );

        assertTrue(ex.getMessage().contains("empty or contains only whitespace"));
    }

    @Test
    void testExecute_BinaryPgDump_ThrowsException() {
        String binaryContent = "PGDMP\u0001\u0002some_binary_data";
        ByteArrayInputStream in = new ByteArrayInputStream(binaryContent.getBytes(StandardCharsets.UTF_8));

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> executor.execute(connection, in, DatabaseType.POSTGRESQL)
        );

        assertTrue(ex.getMessage().contains("binary PostgreSQL custom archive"));
    }

    @Test
    void testExecute_DelimiterDirective_ThrowsException() {
        String sql = "DELIMITER //\nCREATE PROCEDURE test() BEGIN END //\nDELIMITER ;";
        ByteArrayInputStream in = new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8));

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> executor.execute(connection, in, DatabaseType.MYSQL)
        );

        assertTrue(ex.getMessage().contains("DELIMITER"));
    }

    @Test
    void testExecute_PsqlMetaCommand_ThrowsException() {
        String sql = "\\connect testdb\nCREATE TABLE foo (id INT);";
        ByteArrayInputStream in = new ByteArrayInputStream(sql.getBytes(StandardCharsets.UTF_8));

        SchemaImportException ex = assertThrows(
                SchemaImportException.class,
                () -> executor.execute(connection, in, DatabaseType.POSTGRESQL)
        );

        assertTrue(ex.getMessage().contains("Unsupported psql client meta-command '\\connect'"));
    }
}
