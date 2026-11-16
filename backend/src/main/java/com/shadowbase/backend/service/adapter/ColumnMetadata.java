package com.shadowbase.backend.service.adapter;

/**
 * Metadata record for a table column used to determine insertability and type compatibility.
 *
 * @param name            column name
 * @param dataType        JDBC type code from {@link java.sql.Types}
 * @param typeName        database-specific type name (e.g. "VARCHAR", "INT4")
 * @param nullable        whether the column allows NULL values
 * @param isAutoIncrement whether the column is an auto-incrementing / identity column
 * @param isGenerated     whether the column is a generated/computed/virtual column
 */
public record ColumnMetadata(
        String name,
        int dataType,
        String typeName,
        boolean nullable,
        boolean isAutoIncrement,
        boolean isGenerated
) {
    /**
     * Determines whether this column can accept an explicit value in an INSERT statement.
     * Generated columns cannot be inserted into.
     *
     * @return true if insertable; false otherwise
     */
    public boolean isInsertable() {
        return !isGenerated;
    }
}
