package com.shadowbase.backend.service.adapter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Exception.DataSeedingException;

/**
 * Unit tests for {@link DatabaseDialectAdapter} implementations and {@link DatabaseDialectAdapterFactory}.
 */
class DatabaseDialectAdapterTest {

    private PostgreSqlDialectAdapter postgresAdapter;
    private MySqlDialectAdapter mysqlAdapter;
    private DatabaseDialectAdapterFactory factory;

    @BeforeEach
    void setUp() {
        postgresAdapter = new PostgreSqlDialectAdapter();
        mysqlAdapter = new MySqlDialectAdapter();
        factory = new DatabaseDialectAdapterFactory(List.of(postgresAdapter, mysqlAdapter));
    }

    @Test
    void testPostgresQuoting() {
        assertEquals("\"users\"", postgresAdapter.quoteIdentifier("users"));
        assertEquals("\"user\"\"table\"", postgresAdapter.quoteIdentifier("user\"table"));
    }

    @Test
    void testMySqlQuoting() {
        assertEquals("`users`", mysqlAdapter.quoteIdentifier("users"));
        assertEquals("`user``table`", mysqlAdapter.quoteIdentifier("user`table"));
    }

    @Test
    void testFactoryResolvesAdapters() {
        assertNotNull(factory.getAdapter(DatabaseType.POSTGRESQL));
        assertEquals(DatabaseType.POSTGRESQL, factory.getAdapter(DatabaseType.POSTGRESQL).getDatabaseType());

        assertNotNull(factory.getAdapter(DatabaseType.MYSQL));
        assertEquals(DatabaseType.MYSQL, factory.getAdapter(DatabaseType.MYSQL).getDatabaseType());
    }

    @Test
    void testCrossEngineValidationFails() {
        assertThrows(DataSeedingException.class, () ->
                factory.validateCompatibility(DatabaseType.POSTGRESQL, DatabaseType.MYSQL));

        assertThrows(DataSeedingException.class, () ->
                factory.validateCompatibility(DatabaseType.MYSQL, DatabaseType.POSTGRESQL));
    }

    @Test
    void testSameEngineValidationPasses() {
        factory.validateCompatibility(DatabaseType.POSTGRESQL, DatabaseType.POSTGRESQL);
        factory.validateCompatibility(DatabaseType.MYSQL, DatabaseType.MYSQL);
    }
}
