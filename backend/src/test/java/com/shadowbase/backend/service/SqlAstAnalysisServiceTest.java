package com.shadowbase.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shadowbase.backend.dto.DroppedColumnTarget;
import com.shadowbase.backend.dto.MigrationFindingDto;

class SqlAstAnalysisServiceTest {

    private ColumnUsageTracker tracker;
    private SqlAstAnalysisService astService;

    @BeforeEach
    void setUp() {
        tracker = new ColumnUsageTrackerImpl(10);
        astService = new SqlAstAnalysisServiceImpl(tracker, 10);
    }

    @Test
    void shouldExtractSingleDroppedColumn() {
        String sql = "ALTER TABLE users DROP COLUMN email;";
        List<DroppedColumnTarget> dropped = astService.extractDroppedColumns(sql);

        assertEquals(1, dropped.size());
        assertEquals("users", dropped.get(0).getTable());
        assertEquals("email", dropped.get(0).getColumn());
    }

    @Test
    void shouldExtractMultipleDroppedColumnsInSingleStatement() {
        String sql = "ALTER TABLE users DROP COLUMN email, DROP COLUMN phone;";
        List<DroppedColumnTarget> dropped = astService.extractDroppedColumns(sql);

        assertEquals(2, dropped.size());
        assertEquals("users", dropped.get(0).getTable());
        assertEquals("email", dropped.get(0).getColumn());
        assertEquals("users", dropped.get(1).getTable());
        assertEquals("phone", dropped.get(1).getColumn());
    }

    @Test
    void shouldExtractDroppedColumnWithIfExists() {
        String sql = "ALTER TABLE customers DROP COLUMN IF EXISTS address;";
        List<DroppedColumnTarget> dropped = astService.extractDroppedColumns(sql);

        assertEquals(1, dropped.size());
        assertEquals("customers", dropped.get(0).getTable());
        assertEquals("address", dropped.get(0).getColumn());
    }

    @Test
    void shouldWarnWhenDroppingHeavilyQueriedColumn() {
        // Record 25 queries for users.email (exceeds threshold 10)
        tracker.recordColumnAccess("users", "email", 25L);

        String sql = "ALTER TABLE users DROP COLUMN email;";
        List<MigrationFindingDto> findings = astService.analyzeColumnDropRisks(sql, 10);

        assertEquals(1, findings.size());
        MigrationFindingDto finding = findings.get(0);
        assertEquals("high", finding.getLevel());
        assertEquals("DROPPED_HEAVILY_QUERIED_COLUMN", finding.getRule());
        assertTrue(finding.getMessage().contains("heavily queried"));
        assertTrue(finding.getMessage().contains("25 recent queries"));
        assertEquals("users", finding.getTable());
    }

    @Test
    void shouldWarnWhenDroppingActiveColumnBelowHeavyThreshold() {
        // Record 3 queries for users.last_login (below threshold 10)
        tracker.recordColumnAccess("users", "last_login", 3L);

        String sql = "ALTER TABLE users DROP COLUMN last_login;";
        List<MigrationFindingDto> findings = astService.analyzeColumnDropRisks(sql, 10);

        assertEquals(1, findings.size());
        MigrationFindingDto finding = findings.get(0);
        assertEquals("DROPPED_QUERIED_COLUMN", finding.getRule());
        assertTrue(finding.getMessage().contains("3 recent queries"));
    }

    @Test
    void shouldNotWarnWhenColumnHasZeroQueries() {
        String sql = "ALTER TABLE users DROP COLUMN unused_flag;";
        List<MigrationFindingDto> findings = astService.analyzeColumnDropRisks(sql, 10);

        assertTrue(findings.isEmpty());
    }

    @Test
    void shouldHandleNonAlterStatementsGracefully() {
        String sql = "CREATE TABLE test (id INT); INSERT INTO test VALUES (1);";
        List<DroppedColumnTarget> dropped = astService.extractDroppedColumns(sql);

        assertTrue(dropped.isEmpty());
    }

    @Test
    void shouldHandleInvalidSqlWithoutCrashing() {
        String sql = "MALFORMED SQL NOT VALID SYNTAX {{{";
        List<DroppedColumnTarget> dropped = astService.extractDroppedColumns(sql);

        assertTrue(dropped.isEmpty());
    }
}
