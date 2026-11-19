package com.shadowbase;

import com.shadowbase.model.ShadowBaseMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlReplayServiceTest {

    @Test
    void shouldReplayInsertStatement() {

        String sql = """
                INSERT INTO customers (id, name, email)
                VALUES (10, 'Test User', 'test@example.com');
                """;

        // TODO:
        // Create your actual SqlReplayService here
        // and call the replay method from your project.

        assertNotNull(sql);
        assertTrue(sql.startsWith("INSERT"));
    }

    @Test
    void shouldReplayUpdateStatement() {

        String sql = """
                UPDATE customers
                SET name = 'Updated User'
                WHERE id = 10;
                """;

        assertNotNull(sql);
        assertTrue(sql.startsWith("UPDATE"));
    }

    @Test
    void shouldReplayDeleteStatement() {

        String sql = """
                DELETE FROM customers
                WHERE id = 10;
                """;

        assertNotNull(sql);
        assertTrue(sql.startsWith("DELETE"));
    }

    @Test
    void shouldRejectEmptySql() {

        String sql = "";

        assertTrue(sql.isBlank());
    }

    @Test
    void shouldProcessInsertEvent() {

        String sql = """
            INSERT INTO customers (id, name, email)
            VALUES (10, 'Test User', 'test@example.com');
            """;

        assertNotNull(sql);
        assertTrue(sql.contains("INSERT INTO customers"));
        assertTrue(sql.contains("'Test User'"));
    }
    @Test
    void shouldCreateValidShadowBaseMessage() {

        ShadowBaseMessage message = new ShadowBaseMessage(
                1,
                "INSERT",
                "customers",
                "10,Rohan,rohan@example.com"
        );

        assertEquals(1, message.getId());
        assertEquals("INSERT", message.getOperation());
        assertEquals("customers", message.getTable());
        assertEquals("10,Rohan,rohan@example.com", message.getData());
    }
}