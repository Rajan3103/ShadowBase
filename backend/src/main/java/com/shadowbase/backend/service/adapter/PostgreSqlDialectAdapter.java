package com.shadowbase.backend.service.adapter;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.shadowbase.backend.Entity.ConsistencyMode;
import com.shadowbase.backend.Entity.Container.DatabaseType;

import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL implementation of {@link DatabaseDialectAdapter}.
 * <p>
 * Configures cursor-based streaming (requires autoCommit=false), double-quote identifier escaping,
 * generated column filtering, and PostgreSQL sequence synchronization via {@code pg_get_serial_sequence}.
 */
@Slf4j
@Component
public class PostgreSqlDialectAdapter implements DatabaseDialectAdapter {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be empty");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void configureSourceConnection(Connection connection, ConsistencyMode consistencyMode) throws SQLException {
        connection.setReadOnly(true);
        // PostgreSQL requires autoCommit to be false for cursor-based streaming to function
        connection.setAutoCommit(false);

        if (consistencyMode == ConsistencyMode.SNAPSHOT) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        } else {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }
    }

    @Override
    public void configureSourceStatement(Statement statement, int batchSize) throws SQLException {
        // In PostgreSQL, setting fetchSize on a statement with autoCommit=false enables cursor streaming
        statement.setFetchSize(batchSize > 0 ? batchSize : 500);
    }

    @Override
    public List<ColumnMetadata> getInsertableColumns(Connection connection, String tableName) throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();

        // Search case-insensitively for tableName
        try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                int dataType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                int nullableInt = rs.getInt("NULLABLE");
                boolean nullable = (nullableInt == DatabaseMetaData.columnNullable);

                boolean isAutoIncrement = false;
                try {
                    String isAutoincrementStr = rs.getString("IS_AUTOINCREMENT");
                    isAutoIncrement = "YES".equalsIgnoreCase(isAutoincrementStr);
                } catch (Exception ignored) {
                }

                boolean isGenerated = false;
                try {
                    String isGeneratedStr = rs.getString("IS_GENERATEDCOLUMN");
                    isGenerated = "YES".equalsIgnoreCase(isGeneratedStr);
                } catch (Exception ignored) {
                }

                columns.add(new ColumnMetadata(colName, dataType, typeName, nullable, isAutoIncrement, isGenerated));
            }
        }

        // If no columns found with original case, retry with lowercase
        if (columns.isEmpty()) {
            try (ResultSet rs = metaData.getColumns(null, null, tableName.toLowerCase(), null)) {
                while (rs.next()) {
                    String colName = rs.getString("COLUMN_NAME");
                    int dataType = rs.getInt("DATA_TYPE");
                    String typeName = rs.getString("TYPE_NAME");
                    int nullableInt = rs.getInt("NULLABLE");
                    boolean nullable = (nullableInt == DatabaseMetaData.columnNullable);

                    boolean isAutoIncrement = false;
                    try {
                        String isAutoincrementStr = rs.getString("IS_AUTOINCREMENT");
                        isAutoIncrement = "YES".equalsIgnoreCase(isAutoincrementStr);
                    } catch (Exception ignored) {
                    }

                    boolean isGenerated = false;
                    try {
                        String isGeneratedStr = rs.getString("IS_GENERATEDCOLUMN");
                        isGenerated = "YES".equalsIgnoreCase(isGeneratedStr);
                    } catch (Exception ignored) {
                    }

                    columns.add(new ColumnMetadata(colName, dataType, typeName, nullable, isAutoIncrement, isGenerated));
                }
            }
        }

        return columns.stream().filter(ColumnMetadata::isInsertable).toList();
    }

    @Override
    public void resetSequencesOrAutoIncrement(Connection connection, String tableName, String primaryKeyColumn, long maxId) throws SQLException {
        if (primaryKeyColumn == null || primaryKeyColumn.isBlank()) {
            return;
        }

        // Find sequence associated with this column
        String findSeqSql = "SELECT pg_get_serial_sequence(?, ?)";
        String sequenceName = null;
        try (PreparedStatement stmt = connection.prepareStatement(findSeqSql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, primaryKeyColumn);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    sequenceName = rs.getString(1);
                }
            }
        } catch (Exception e) {
            log.debug("No serial sequence found for {}.{}: {}", tableName, primaryKeyColumn, e.getMessage());
        }

        if (sequenceName != null && !sequenceName.isBlank()) {
            long nextVal = Math.max(maxId, 1);
            String resetSql = "SELECT setval(?, ?, true)";
            try (PreparedStatement stmt = connection.prepareStatement(resetSql)) {
                stmt.setString(1, sequenceName);
                stmt.setLong(2, nextVal);
                stmt.execute();
                log.info("Reset sequence '{}' to {} for table '{}'", sequenceName, nextVal, tableName);
            }
        }
    }
}
