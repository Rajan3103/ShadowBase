package com.shadowbase.backend.service.seeding;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.shadowbase.backend.Entity.Container.DatabaseType;
import com.shadowbase.backend.Exception.DataSeedingException;
import com.shadowbase.backend.service.adapter.ColumnMetadata;
import com.shadowbase.backend.service.adapter.DatabaseDialectAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * Component responsible for inspecting and comparing database schemas between
 * external source databases and target sandbox containers using JDBC {@link DatabaseMetaData}.
 */
@Slf4j
@Component
public class SchemaInspector {

    private static final Set<String> POSTGRES_SYSTEM_SCHEMAS = Set.of("pg_catalog", "information_schema");
    private static final Set<String> MYSQL_SYSTEM_SCHEMAS = Set.of("information_schema", "mysql", "performance_schema", "sys");

    /**
     * Discovers all user-defined relational tables in the source database, filtering out system tables.
     *
     * @param connection active source JDBC connection
     * @param dbType     database engine type
     * @return list of table names found in the database
     * @throws SQLException if metadata query fails
     */
    public List<String> getSourceTables(Connection connection, DatabaseType dbType) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = connection.getMetaData();

        try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String catalog = rs.getString("TABLE_CAT");
                String tableName = rs.getString("TABLE_NAME");

                if (isSystemTable(schema, catalog, dbType)) {
                    continue;
                }

                if (tableName != null && !tableName.isBlank() && !tables.contains(tableName)) {
                    tables.add(tableName);
                }
            }
        }
        return tables;
    }

    /**
     * Validates that all requested tables exist in both source and target databases.
     *
     * @param sourceConn source connection
     * @param targetConn target sandbox connection
     * @param tables     list of tables requested for transfer
     * @param dbType     database type
     * @throws DataSeedingException if any requested table is missing from either database
     */
    public void validateTablesExist(Connection sourceConn, Connection targetConn, List<String> tables, DatabaseType dbType)
            throws SQLException {
        if (tables == null || tables.isEmpty()) {
            throw new DataSeedingException("No tables specified for data seeding");
        }

        Set<String> sourceTables = new HashSet<>(getSourceTables(sourceConn, dbType));
        Set<String> targetTables = new HashSet<>(getSourceTables(targetConn, dbType));

        List<String> missingInSource = new ArrayList<>();
        List<String> missingInTarget = new ArrayList<>();

        for (String table : tables) {
            boolean inSource = containsCaseInsensitive(sourceTables, table);
            boolean inTarget = containsCaseInsensitive(targetTables, table);

            if (!inSource) {
                missingInSource.add(table);
            }
            if (!inTarget) {
                missingInTarget.add(table);
            }
        }

        if (!missingInSource.isEmpty() || !missingInTarget.isEmpty()) {
            StringBuilder sb = new StringBuilder("Table validation failed: ");
            if (!missingInSource.isEmpty()) {
                sb.append("Missing in source: ").append(missingInSource).append(". ");
            }
            if (!missingInTarget.isEmpty()) {
                sb.append("Missing in target sandbox (schema not imported?): ").append(missingInTarget).append(".");
            }
            throw new DataSeedingException(sb.toString());
        }
    }

    /**
     * Validates column compatibility between source and target for a specific table.
     * Ensures all insertable columns in the target table exist in the source table.
     *
     * @param sourceConn source connection
     * @param targetConn target connection
     * @param tableName  table name
     * @param adapter    dialect adapter
     * @throws DataSeedingException if column mismatch is detected
     */
    public void validateColumnCompatibility(Connection sourceConn, Connection targetConn, String tableName,
                                            DatabaseDialectAdapter adapter) throws SQLException {
        List<ColumnMetadata> targetColumns = adapter.getInsertableColumns(targetConn, tableName);
        List<ColumnMetadata> sourceColumns = adapter.getInsertableColumns(sourceConn, tableName);

        Map<String, ColumnMetadata> sourceColMap = new HashMap<>();
        for (ColumnMetadata col : sourceColumns) {
            sourceColMap.put(col.name().toLowerCase(), col);
        }

        List<String> missingColumns = new ArrayList<>();
        for (ColumnMetadata targetCol : targetColumns) {
            ColumnMetadata sourceCol = sourceColMap.get(targetCol.name().toLowerCase());
            if (sourceCol == null) {
                // If the target column is NOT NULL and has no default value, it's a fatal mismatch
                if (!targetCol.nullable()) {
                    missingColumns.add(targetCol.name() + " (NOT NULL in target)");
                } else {
                    log.warn("Target column '{}.{}' does not exist in source table; will be set to NULL/DEFAULT",
                            tableName, targetCol.name());
                }
            }
        }

        if (!missingColumns.isEmpty()) {
            throw new DataSeedingException(String.format(
                    "Schema incompatibility in table '%s': Target requires non-null columns not present in source: %s",
                    tableName, missingColumns
            ));
        }
    }

    /**
     * Verifies that all target tables are currently empty (0 rows).
     * Rejects already populated tables to prevent accidental duplicate inserts or constraint violations.
     *
     * @param targetConn target connection
     * @param tables     tables to check
     * @param adapter    dialect adapter
     * @throws DataSeedingException if any target table contains rows
     */
    public void verifyTargetTablesEmpty(Connection targetConn, List<String> tables, DatabaseDialectAdapter adapter)
            throws SQLException {
        for (String table : tables) {
            String sql = "SELECT COUNT(*) FROM " + adapter.quoteIdentifier(table);
            try (Statement stmt = targetConn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    long count = rs.getLong(1);
                    if (count > 0) {
                        throw new DataSeedingException(String.format(
                                "Target table '%s' is not empty (contains %d rows). Data seeding requires an empty target table to avoid collisions.",
                                table, count
                        ));
                    }
                }
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("DataSeedingException")) {
                    throw e;
                }
                log.warn("Could not check row count for table '{}': {}", table, e.getMessage());
            }
        }
    }

    /**
     * Retrieves the single primary key column name for a table, or null if compound or not defined.
     */
    public String getPrimaryKeyColumn(Connection conn, String tableName) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getPrimaryKeys(null, null, tableName)) {
                if (rs.next()) {
                    return rs.getString("COLUMN_NAME");
                }
            }
            // Fallback for case sensitivity
            try (ResultSet rs = meta.getPrimaryKeys(null, null, tableName.toLowerCase())) {
                if (rs.next()) {
                    return rs.getString("COLUMN_NAME");
                }
            }
        } catch (Exception e) {
            log.debug("Primary key query failed for '{}': {}", tableName, e.getMessage());
        }
        return null;
    }

    private boolean isSystemTable(String schema, String catalog, DatabaseType dbType) {
        if (dbType == DatabaseType.POSTGRESQL && schema != null) {
            return POSTGRES_SYSTEM_SCHEMAS.contains(schema.toLowerCase());
        }
        if (dbType == DatabaseType.MYSQL && catalog != null) {
            return MYSQL_SYSTEM_SCHEMAS.contains(catalog.toLowerCase());
        }
        return false;
    }

    private boolean containsCaseInsensitive(Set<String> set, String target) {
        for (String s : set) {
            if (s.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
