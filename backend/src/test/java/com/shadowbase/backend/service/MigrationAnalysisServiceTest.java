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

    @Test
    void testDroppedHeavilyQueriedColumnWarnsWithCriticalFinding() {
        ColumnUsageTracker tracker = new ColumnUsageTrackerImpl(10);
        SqlAstAnalysisService astService = new SqlAstAnalysisServiceImpl(tracker, 10);
        MigrationAnalysisServiceImpl serviceWithAst = new MigrationAnalysisServiceImpl(astService, tracker);

        // Record 30 queries on users.email
        tracker.recordColumnAccess("users", "email", 30L);

        String sql = "ALTER TABLE users DROP COLUMN email;";
        MigrationAnalysisResponse res = serviceWithAst.analyzeMigration(new MigrationAnalysisRequest(sql));

        assertEquals(1, res.getHighRisks());
        assertTrue(res.isHasBreakingRisks());
        assertEquals("DROPPED_HEAVILY_QUERIED_COLUMN", res.getFindings().get(0).getRule());
        assertTrue(res.getFindings().get(0).getMessage().contains("30 recent queries"));
    }

    @Test
    void testDroppedQueriedColumnBelowHeavyThresholdWarnsWithActiveTraffic() {
        ColumnUsageTracker tracker = new ColumnUsageTrackerImpl(10);
        SqlAstAnalysisService astService = new SqlAstAnalysisServiceImpl(tracker, 10);
        MigrationAnalysisServiceImpl serviceWithAst = new MigrationAnalysisServiceImpl(astService, tracker);

        // Record 4 queries on users.avatar (below threshold 10)
        tracker.recordColumnAccess("users", "avatar", 4L);

        String sql = "ALTER TABLE users DROP COLUMN avatar;";
        MigrationAnalysisResponse res = serviceWithAst.analyzeMigration(new MigrationAnalysisRequest(sql));

        assertEquals(1, res.getHighRisks());
        assertTrue(res.isHasBreakingRisks());
        assertEquals("DROPPED_QUERIED_COLUMN", res.getFindings().get(0).getRule());
        assertTrue(res.getFindings().get(0).getMessage().contains("4 recent queries"));
    }

    @Test
    void testRenamedHeavilyQueriedColumnWarnsWithCriticalFinding() {
        ColumnUsageTracker tracker = new ColumnUsageTrackerImpl(10);
        SqlAstAnalysisService astService = new SqlAstAnalysisServiceImpl(tracker, 10);
        MigrationAnalysisServiceImpl serviceWithAst = new MigrationAnalysisServiceImpl(astService, tracker);

        // Record 40 queries on customers.phone
        tracker.recordColumnAccess("customers", "phone", 40L);

        String sql = "ALTER TABLE customers RENAME COLUMN phone TO mobile_phone;";
        MigrationAnalysisResponse res = serviceWithAst.analyzeMigration(new MigrationAnalysisRequest(sql));

        assertEquals(1, res.getHighRisks());
        assertTrue(res.isHasBreakingRisks());
        assertEquals("RENAMED_HEAVILY_QUERIED_COLUMN", res.getFindings().get(0).getRule());
        assertTrue(res.getFindings().get(0).getMessage().contains("40 recent queries"));
        assertEquals("phone", res.getFindings().get(0).getColumn());
        assertTrue(res.getFindings().get(0).getSuggestedFix().contains("Expand-Contract"));
    }
}
