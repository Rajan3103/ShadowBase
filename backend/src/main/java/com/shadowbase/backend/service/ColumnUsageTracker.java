package com.shadowbase.backend.service;

import java.util.Map;

/**
 * Service tracking query traffic and column access frequencies.
 * <p>
 * Provides a foundation for AST-based risk analysis, identifying heavily
 * queried columns before destructive DDL migration operations are executed.
 */
public interface ColumnUsageTracker {

    /**
     * Records a single query access for the specified table and column.
     *
     * @param table  the database table name
     * @param column the accessed column name
     */
    void recordColumnAccess(String table, String column);

    /**
     * Records multiple query accesses for the specified table and column.
     *
     * @param table  the database table name
     * @param column the accessed column name
     * @param count  number of query accesses to increment
     */
    void recordColumnAccess(String table, String column, long count);

    /**
     * Parses an SQL query (e.g. SELECT, INSERT, UPDATE) using JSqlParser AST
     * and automatically increments access counters for all referenced columns.
     *
     * @param sql the executed SQL query
     */
    void recordQueryColumns(String sql);

    /**
     * Retrieves the total recorded access count for a table column.
     *
     * @param table  the database table name
     * @param column the column name
     * @return total query access count
     */
    long getQueryCount(String table, String column);

    /**
     * Checks if a column is considered heavily queried under the default threshold.
     *
     * @param table  the database table name
     * @param column the column name
     * @return true if access count is greater than or equal to default threshold
     */
    boolean isColumnHeavilyQueried(String table, String column);

    /**
     * Checks if a column is considered heavily queried under a custom threshold.
     *
     * @param table     the database table name
     * @param column    the column name
     * @param threshold minimum query count threshold
     * @return true if access count is greater than or equal to threshold
     */
    boolean isColumnHeavilyQueried(String table, String column, int threshold);

    /**
     * Returns a map of column names to recorded query counts for a given table.
     *
     * @param table the database table name
     * @return map of column names to query counts
     */
    Map<String, Long> getTableColumnUsage(String table);

    /**
     * Resets all tracked column access metrics.
     */
    void reset();
}
