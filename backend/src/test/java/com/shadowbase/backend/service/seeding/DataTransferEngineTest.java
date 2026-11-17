package com.shadowbase.backend.service.seeding;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.shadowbase.backend.Entity.SeedTableProgress;
import com.shadowbase.backend.Entity.SeedTableStatus;
import com.shadowbase.backend.Exception.DataSeedingException;
import com.shadowbase.backend.service.adapter.ColumnMetadata;
import com.shadowbase.backend.service.adapter.DatabaseDialectAdapter;

/**
 * Unit tests for {@link DataTransferEngine}.
 * Tests streaming result set reading, batching, sequence reset, and cancellation at batch boundaries.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataTransferEngineTest {

    @Mock
    private Connection sourceConn;

    @Mock
    private Connection targetConn;

    @Mock
    private DatabaseDialectAdapter adapter;

    @Mock
    private Statement sourceStmt;

    @Mock
    private ResultSet sourceRs;

    @Mock
    private ResultSetMetaData rsMeta;

    @Mock
    private PreparedStatement targetPstmt;

    private DataTransferEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        engine = new DataTransferEngine();

        // Set batch size via reflection
        Field batchField = DataTransferEngine.class.getDeclaredField("configuredBatchSize");
        batchField.setAccessible(true);
        batchField.set(engine, 500);

        Field maxBatchField = DataTransferEngine.class.getDeclaredField("maxBatchSize");
        maxBatchField.setAccessible(true);
        maxBatchField.set(engine, 5000);

        when(adapter.quoteIdentifier(anyString())).thenAnswer(inv -> "\"" + inv.getArgument(0) + "\"");
        when(sourceConn.createStatement(anyInt(), anyInt())).thenReturn(sourceStmt);
        when(sourceStmt.executeQuery(anyString())).thenReturn(sourceRs);
        when(sourceRs.getMetaData()).thenReturn(rsMeta);
        when(targetConn.prepareStatement(anyString())).thenReturn(targetPstmt);
    }

    @Test
    void testTransferTable_SuccessWithRows() throws SQLException {
        String tableName = "users";
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id", 4, "INT", false, true, false),
                new ColumnMetadata("name", 12, "VARCHAR", true, false, false)
        );
        when(adapter.getInsertableColumns(targetConn, tableName)).thenReturn(columns);

        // Simulate 2 rows
        when(sourceRs.next()).thenReturn(true, true, false);
        when(sourceRs.getObject(1)).thenReturn(1L, 2L);
        when(sourceRs.getObject(2)).thenReturn("Alice", "Bob");

        SeedTableProgress progress = new SeedTableProgress();
        progress.setTableName(tableName);

        engine.transferTable(sourceConn, targetConn, tableName, adapter, "id", progress, () -> false, p -> {});

        assertEquals(SeedTableStatus.SUCCESS, progress.getStatus());
        assertEquals(2L, progress.getRowsRead());
        assertEquals(2L, progress.getRowsInserted());
        verify(targetPstmt, atLeastOnce()).addBatch();
        verify(targetConn, atLeastOnce()).commit();
        verify(adapter).resetSequencesOrAutoIncrement(targetConn, tableName, "id", 2L);
    }

    @Test
    void testTransferTable_CancellationTriggered() throws SQLException {
        String tableName = "logs";
        List<ColumnMetadata> columns = List.of(
                new ColumnMetadata("id", 4, "INT", false, true, false)
        );
        when(adapter.getInsertableColumns(targetConn, tableName)).thenReturn(columns);

        when(sourceRs.next()).thenReturn(true);
        AtomicBoolean cancelled = new AtomicBoolean(true);

        SeedTableProgress progress = new SeedTableProgress();
        progress.setTableName(tableName);

        assertThrows(DataSeedingException.class, () ->
                engine.transferTable(sourceConn, targetConn, tableName, adapter, "id", progress, cancelled::get, p -> {}));

        assertEquals(SeedTableStatus.SKIPPED, progress.getStatus());
        verify(targetConn).rollback();
    }
}
