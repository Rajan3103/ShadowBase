package com.shadowbase.backend.service.adapter;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import com.shadowbase.backend.Entity.ConsistencyMode;
import com.shadowbase.backend.Entity.Container.DatabaseType;

/**
 * Strategy interface isolating database-engine-specific behaviors during data seeding.
 * <p>
 * Handles identifier quoting, cursor-based streaming configuration, insertable column discovery,
 * and sequence/auto-increment counter synchronization.
 */
public interface DatabaseDialectAdapter {

    /**
     * The database type handled by this adapter.
     *
     * @return DatabaseType (POSTGRESQL or MYSQL)
     */
    DatabaseType getDatabaseType();

    /**
     * Safely quotes a table or column SQL identifier to prevent keyword conflicts and SQL injection.
     *
     * @param identifier table or column name
     * @return safely quoted identifier (e.g., "table" for PostgreSQL, `table` for MySQL)
     */
    String quoteIdentifier(String identifier);

    /**
     * Configures transaction isolation and read-only flags on the source connection.
     *
     * @param connection      active source JDBC connection
     * @param consistencyMode requested consistency mode (SNAPSHOT or STANDARD)
     * @throws SQLException if a configuration error occurs
     */
    void configureSourceConnection(Connection connection, ConsistencyMode consistencyMode) throws SQLException;

    /**
     * Configures a source SQL {@link Statement} for forward-only streaming to avoid loading
     * entire result sets into memory.
     *
     * @param statement active statement
     * @param batchSize configured server-side batch size
     * @throws SQLException if configuration fails
     */
    void configureSourceStatement(Statement statement, int batchSize) throws SQLException;

    /**
     * Inspects and retrieves all insertable columns for a specific table.
     * Filters out generated / computed columns that cannot accept explicit INSERT values.
     *
     * @param connection target JDBC connection
     * @param tableName  table name
     * @return list of insertable ColumnMetadata in ordinal order
     * @throws SQLException if metadata extraction fails
     */
    List<ColumnMetadata> getInsertableColumns(Connection connection, String tableName) throws SQLException;

    /**
     * Resets primary key sequence or auto-increment counter on the target table after all
     * rows have been transferred so future application inserts do not conflict with transferred IDs.
     *
     * @param connection       target JDBC connection
     * @param tableName        table name
     * @param primaryKeyColumn primary key column name
     * @param maxId            maximum transferred ID value
     * @throws SQLException if counter reset fails
     */
    void resetSequencesOrAutoIncrement(Connection connection, String tableName, String primaryKeyColumn, long maxId) throws SQLException;
}
