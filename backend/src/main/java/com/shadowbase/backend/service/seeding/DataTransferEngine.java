package com.shadowbase.backend.service.seeding;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.shadowbase.backend.Entity.SeedJob;
import com.shadowbase.backend.Entity.SeedTableProgress;
import com.shadowbase.backend.Entity.SeedTableStatus;
import com.shadowbase.backend.Exception.DataSeedingException;
import com.shadowbase.backend.service.adapter.ColumnMetadata;
import com.shadowbase.backend.service.adapter.DatabaseDialectAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * Core data transfer engine responsible for streaming records from the source database
 * and batch-inserting them into the target sandbox container database.
 * <p>
 * Key performance and safety guarantees:
 * <ul>
 *   <li>Streams records using a forward-only {@link ResultSet} without loading tables into memory.</li>
 *   <li>Flushes inserts using bounded {@link PreparedStatement} batches (default 500 rows).</li>
 *   <li>Commits transactions periodically at batch boundaries to avoid holding long-lived locks.</li>
 *   <li>Resets target sequence / auto-increment counters after table data is committed.</li>
 *   <li>Checks cancellation flags at each batch boundary for graceful interruption.</li>
 * </ul>
 */
@Slf4j
@Component
public class DataTransferEngine {

    @Value("${shadowbase.seeding.batch-size:500}")
    private int configuredBatchSize;

    @Value("${shadowbase.seeding.max-batch-size:5000}")
    private int maxBatchSize;

    /**
     * Transfers data for a single table from source to target.
     *
     * @param sourceConn          active read-only source connection
     * @param targetConn          active target connection with manual transaction control
     * @param tableName           name of the table to copy
     * @param adapter             database dialect adapter for quoting, streaming, and sequence resets
     * @param pkColumnName        primary key column name if available, or null
     * @param progress            in-memory progress tracker for this table
     * @param isCancelledSupplier callback returning true if the parent job was requested to cancel
     * @param progressCallback    callback invoked when progress counters update
     * @throws SQLException         if a database access error occurs
     * @throws DataSeedingException if cancellation or constraint violation occurs
     */
    public void transferTable(Connection sourceConn,
                              Connection targetConn,
                              String tableName,
                              DatabaseDialectAdapter adapter,
                              String pkColumnName,
                              SeedTableProgress progress,
                              BooleanSupplier isCancelledSupplier,
                              Consumer<SeedTableProgress> progressCallback) throws SQLException {

        log.info("Starting data transfer for table '{}'...", tableName);
        progress.setStatus(SeedTableStatus.RUNNING);
        progress.setStartedAt(LocalDateTime.now());
        if (progressCallback != null) {
            progressCallback.accept(progress);
        }

        int batchSize = Math.min(Math.max(configuredBatchSize, 100), maxBatchSize);

        List<ColumnMetadata> insertableColumns = adapter.getInsertableColumns(targetConn, tableName);
        if (insertableColumns.isEmpty()) {
            log.warn("Table '{}' has no insertable columns. Marking SUCCESS.", tableName);
            progress.setStatus(SeedTableStatus.SUCCESS);
            progress.setCompletedAt(LocalDateTime.now());
            if (progressCallback != null) {
                progressCallback.accept(progress);
            }
            return;
        }

        String selectSql = buildSelectSql(tableName, insertableColumns, adapter);
        String insertSql = buildInsertSql(tableName, insertableColumns, adapter);

        long maxPkValue = 0L;
        boolean hasNumericPk = (pkColumnName != null && !pkColumnName.isBlank());

        // Target connection uses bounded transactions committed at batch boundaries
        targetConn.setAutoCommit(false);

        try (Statement sourceStmt = sourceConn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            adapter.configureSourceStatement(sourceStmt, batchSize);

            try (ResultSet rs = sourceStmt.executeQuery(selectSql);
                 PreparedStatement insertStmt = targetConn.prepareStatement(insertSql)) {

                ResultSetMetaData rsMeta = rs.getMetaData();
                int colCount = insertableColumns.size();

                long rowsRead = 0;
                long rowsInserted = 0;
                int batchCount = 0;

                while (rs.next()) {
                    if (isCancelledSupplier.getAsBoolean()) {
                        targetConn.rollback();
                        progress.setStatus(SeedTableStatus.SKIPPED);
                        progress.setErrorMessage("Transfer cancelled by user");
                        progress.setCompletedAt(LocalDateTime.now());
                        if (progressCallback != null) {
                            progressCallback.accept(progress);
                        }
                        throw new DataSeedingException("Data seeding was cancelled during table '" + tableName + "'");
                    }

                    rowsRead++;

                    for (int i = 0; i < colCount; i++) {
                        Object val = rs.getObject(i + 1);
                        insertStmt.setObject(i + 1, val);

                        if (hasNumericPk && insertableColumns.get(i).name().equalsIgnoreCase(pkColumnName)) {
                            if (val instanceof Number num) {
                                maxPkValue = Math.max(maxPkValue, num.longValue());
                            }
                        }
                    }

                    insertStmt.addBatch();
                    batchCount++;

                    if (batchCount >= batchSize) {
                        insertStmt.executeBatch();
                        targetConn.commit();
                        rowsInserted += batchCount;
                        batchCount = 0;

                        progress.setRowsRead(rowsRead);
                        progress.setRowsInserted(rowsInserted);
                        if (progressCallback != null) {
                            progressCallback.accept(progress);
                        }
                    }
                }

                // Flush remaining rows in batch
                if (batchCount > 0) {
                    insertStmt.executeBatch();
                    targetConn.commit();
                    rowsInserted += batchCount;
                }

                progress.setRowsRead(rowsRead);
                progress.setRowsInserted(rowsInserted);
                progress.setStatus(SeedTableStatus.SUCCESS);
                progress.setCompletedAt(LocalDateTime.now());
                if (progressCallback != null) {
                    progressCallback.accept(progress);
                }

                log.info("Table '{}' transferred successfully: {} rows copied.", tableName, rowsInserted);

                // Reset sequence / auto-increment for this table if applicable
                if (hasNumericPk && maxPkValue > 0) {
                    try {
                        adapter.resetSequencesOrAutoIncrement(targetConn, tableName, pkColumnName, maxPkValue);
                        targetConn.commit();
                    } catch (Exception e) {
                        log.warn("Sequence reset warning for table '{}': {}", tableName, e.getMessage());
                    }
                }

            } catch (SQLException e) {
                targetConn.rollback();
                progress.setStatus(SeedTableStatus.FAILED);
                progress.setErrorMessage("Transfer error: " + e.getMessage());
                progress.setCompletedAt(LocalDateTime.now());
                if (progressCallback != null) {
                    progressCallback.accept(progress);
                }
                throw e;
            }
        }
    }

    private String buildSelectSql(String tableName, List<ColumnMetadata> columns, DatabaseDialectAdapter adapter) {
        StringBuilder sb = new StringBuilder("SELECT ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(adapter.quoteIdentifier(columns.get(i).name()));
        }
        sb.append(" FROM ").append(adapter.quoteIdentifier(tableName));
        return sb.toString();
    }

    private String buildInsertSql(String tableName, List<ColumnMetadata> columns, DatabaseDialectAdapter adapter) {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        sb.append(adapter.quoteIdentifier(tableName)).append(" (");

        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(adapter.quoteIdentifier(columns.get(i).name()));
        }

        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }
}
