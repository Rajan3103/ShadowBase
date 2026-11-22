package com.shadowbase.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReplayResultTest {

    @Test
    void shouldCreateSuccessfulResult() {

        String sql = "INSERT INTO student VALUES (1, 'Rohan', 20);";

        ReplayResult result = ReplayResult.success(sql);

        assertTrue(result.isSuccess());
        assertEquals(sql, result.getSql());
        assertNull(result.getErrorMessage());
    }

    @Test
    void shouldCreateFailedResult() {

        String sql = "INSERT INTO student VALUES (1, 'Rohan', 20);";
        String error = "Table does not exist";

        ReplayResult result =
                ReplayResult.failure(sql, error);

        assertFalse(result.isSuccess());
        assertEquals(sql, result.getSql());
        assertEquals(error, result.getErrorMessage());
    }
}