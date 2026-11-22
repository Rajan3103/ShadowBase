package com.shadowbase.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shadowbase.backend.dto.MigrationAnalysisRequest;
import com.shadowbase.backend.dto.MigrationAnalysisResponse;

class MigrationAnalysisServiceTest {

    private MigrationAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MigrationAnalysisServiceImpl();
    }

    @Test
    void testEmptySqlReturnsZeroRisks() {
        MigrationAnalysisResponse res = service.analyzeMigration(new MigrationAnalysisRequest(""));
        assertEquals(0, res.getTotalRisks());
        assertFalse(res.isHasBreakingRisks());
    }

    @Test
    void testDropTableDetectedAsHighRisk() {
        String sql = "-- drop orders\nDROP TABLE orders;";
        MigrationAnalysisResponse res = service.analyzeMigration(new MigrationAnalysisRequest(sql));
        assertEquals(1, res.getTotalRisks());
        assertEquals(1, res.getHighRisks());
        assertTrue(res.isHasBreakingRisks());
        assertEquals("DROP_TABLE", res.getFindings().get(0).getRule());
        assertEquals("orders", res.getFindings().get(0).getTable());
        assertEquals(2, res.getFindings().get(0).getLine());
    }

    @Test
    void testDropColumnDetectedAsHighRisk() {
        String sql = "ALTER TABLE users DROP COLUMN email;";
        MigrationAnalysisResponse res = service.analyzeMigration(new MigrationAnalysisRequest(sql));
        assertEquals(1, res.getHighRisks());
        assertTrue(res.isHasBreakingRisks());
        assertEquals("DROP_COLUMN", res.getFindings().get(0).getRule());
        assertEquals("users", res.getFindings().get(0).getTable());
    }

    @Test
    void testRenameColumnDetectedAsHighRisk() {
        String sql = "ALTER TABLE customers RENAME COLUMN full_name TO display_name;";
        MigrationAnalysisResponse res = service.analyzeMigration(new MigrationAnalysisRequest(sql));
        assertEquals(1, res.getHighRisks());
        assertTrue(res.isHasBreakingRisks());
        assertEquals("RENAME_COLUMN", res.getFindings().get(0).getRule());
    }

    @Test
    void testAlterColumnTypeAndNotNullDetectedAsMediumRisk() {
        String sql = """
                ALTER TABLE products ALTER COLUMN price TYPE numeric(10,2);
                ALTER TABLE products ALTER COLUMN title SET NOT NULL;
                """;
        MigrationAnalysisResponse res = service.analyzeMigration(new MigrationAnalysisRequest(sql));
        assertEquals(2, res.getMediumRisks());
        assertEquals(0, res.getHighRisks());
        assertFalse(res.isHasBreakingRisks());
    }

    @Test
    void testAddColumnWithDefaultDetectedAsLowRisk() {
        String sql = "ALTER TABLE users ADD COLUMN age INT DEFAULT 18;";
        MigrationAnalysisResponse res = service.analyzeMigration(new MigrationAnalysisRequest(sql));
        assertEquals(1, res.getLowRisks());
        assertEquals(0, res.getHighRisks());
        assertEquals("ADD_COLUMN", res.getFindings().get(0).getRule());
    }

    @Test
    void testMixedMigrationScript() {
        String sql = """
                -- Add safe column
                ALTER TABLE customers ADD COLUMN phone VARCHAR(20);
                -- Add unsafe column without default
                ALTER TABLE customers ADD COLUMN ssn VARCHAR(20) NOT NULL;
                -- Drop old table
                DROP TABLE customer_profiles;
                """;
        MigrationAnalysisResponse res = service.analyzeMigration(new MigrationAnalysisRequest(sql));
        assertEquals(3, res.getTotalRisks());
        assertEquals(1, res.getHighRisks());
        assertEquals(1, res.getMediumRisks());
        assertEquals(1, res.getLowRisks());
        assertTrue(res.isHasBreakingRisks());
    }
}
