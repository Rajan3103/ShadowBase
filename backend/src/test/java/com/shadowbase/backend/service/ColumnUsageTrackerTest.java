package com.shadowbase.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ColumnUsageTrackerTest {

    private ColumnUsageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ColumnUsageTrackerImpl(10);
    }

    @Test
    void shouldRecordAndRetrieveSingleColumnAccess() {
        tracker.recordColumnAccess("users", "email");
        tracker.recordColumnAccess("users", "email");

        assertEquals(2L, tracker.getQueryCount("users", "email"));
        assertEquals(0L, tracker.getQueryCount("users", "unknown_column"));
        assertEquals(0L, tracker.getQueryCount("other_table", "email"));
    }

    @Test
    void shouldRecordBatchColumnAccess() {
        tracker.recordColumnAccess("orders", "total_price", 15L);

        assertEquals(15L, tracker.getQueryCount("orders", "total_price"));
        assertTrue(tracker.isColumnHeavilyQueried("orders", "total_price"));
        assertTrue(tracker.isColumnHeavilyQueried("orders", "total_price", 10));
        assertFalse(tracker.isColumnHeavilyQueried("orders", "total_price", 20));
    }

    @Test
    void shouldExtractColumnsFromSelectQuery() {
        String query = "SELECT id, email, status FROM users WHERE status = 'ACTIVE'";
        tracker.recordQueryColumns(query);

        assertEquals(1L, tracker.getQueryCount("users", "id"));
        assertEquals(1L, tracker.getQueryCount("users", "email"));
        assertEquals(1L, tracker.getQueryCount("users", "status"));
    }

    @Test
    void shouldExtractColumnsFromUpdateQuery() {
        String query = "UPDATE customers SET phone = '1234567890', address = 'Main St' WHERE id = 1";
        tracker.recordQueryColumns(query);

        assertEquals(1L, tracker.getQueryCount("customers", "phone"));
        assertEquals(1L, tracker.getQueryCount("customers", "address"));
    }

    @Test
    void shouldExtractColumnsFromInsertQuery() {
        String query = "INSERT INTO logs (event_type, payload) VALUES ('LOGIN', 'user logged in')";
        tracker.recordQueryColumns(query);

        assertEquals(1L, tracker.getQueryCount("logs", "event_type"));
        assertEquals(1L, tracker.getQueryCount("logs", "payload"));
    }

    @Test
    void shouldReturnTableColumnUsageMap() {
        tracker.recordColumnAccess("accounts", "balance", 20L);
        tracker.recordColumnAccess("accounts", "owner_id", 5L);

        Map<String, Long> stats = tracker.getTableColumnUsage("accounts");
        assertEquals(2, stats.size());
        assertEquals(20L, stats.get("balance"));
        assertEquals(5L, stats.get("owner_id"));
    }

    @Test
    void shouldResetStats() {
        tracker.recordColumnAccess("users", "email", 50L);
        assertEquals(50L, tracker.getQueryCount("users", "email"));

        tracker.reset();
        assertEquals(0L, tracker.getQueryCount("users", "email"));
        assertTrue(tracker.getTableColumnUsage("users").isEmpty());
    }

    @Test
    void shouldHandleCaseInsensitivityAndQuoting() {
        tracker.recordColumnAccess("`Users`", "\"Email\"", 5L);

        assertEquals(5L, tracker.getQueryCount("users", "email"));
        assertEquals(5L, tracker.getQueryCount("USERS", "EMAIL"));
    }
}
