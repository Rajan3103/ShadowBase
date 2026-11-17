package com.shadowbase.backend.service.adapter;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
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
 * MySQL implementation of {@link DatabaseDialectAdapter}.
 * <p>
 * Configures streaming reads via MySQL Connector/J's {@code Integer.MIN_VALUE} fetch size,
 * backtick identifier escaping, generated column detection, and {@code ALTER TABLE AUTO_INCREMENT} resets.
 */
@Slf4j
@Component
public class MySqlDialectAdapter implements DatabaseDialectAdapter {

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be empty");
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public void configureSourceConnection(Connection connection, ConsistencyMode consistencyMode) throws SQLException {
        connection.setReadOnly(true);
        if (consistencyMode == ConsistencyMode.SNAPSHOT) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        } else {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }
    }

    @Override
    public void configureSourceStatement(Statement statement, int batchSize) throws SQLException {
        // MySQL Connector/J requires fetchSize to be Integer.MIN_VALUE for row-by-row streaming
        statement.setFetchSize(Integer.MIN_VALUE);
    }

    @Override
    public List<ColumnMetadata> getInsertableColumns(Connection connection, String tableName) throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();

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

        return columns.stream().filter(ColumnMetadata::isInsertable).toList();
    }

    @Override
    public void resetSequencesOrAutoIncrement(Connection connection, String tableName, String primaryKeyColumn, long maxId) throws SQLException {
        if (maxId <= 0) {
            return;
        }

        long nextAutoIncrement = maxId + 1;
        String sql = "ALTER TABLE " + quoteIdentifier(tableName) + " AUTO_INCREMENT = " + nextAutoIncrement;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            log.info("Reset AUTO_INCREMENT for table '{}' to {}", tableName, nextAutoIncrement);
        } catch (SQLException e) {
            log.warn("Could not reset AUTO_INCREMENT for table '{}': {}", tableName, e.getMessage());
        }
    }
}
