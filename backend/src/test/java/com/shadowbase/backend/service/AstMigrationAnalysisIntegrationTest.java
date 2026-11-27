package com.shadowbase.backend.service;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shadowbase.backend.dto.MigrationAnalysisRequest;
import com.shadowbase.backend.dto.MigrationAnalysisResponse;
import com.shadowbase.backend.dto.MigrationFindingDto;

/**
 * End-to-end integration tests verifying the entire AST SQL parsing, column usage tracking,
 * and migration risk analysis pipeline.
 */
class AstMigrationAnalysisIntegrationTest {

    private ColumnUsageTrackerImpl columnUsageTracker;
    private SqlAstAnalysisServiceImpl sqlAstAnalysisService;
    private MigrationAnalysisServiceImpl migrationAnalysisService;
    private MetricsServiceImpl metricsService;

    private static final int HEAVY_THRESHOLD = 5;

    @BeforeEach
    void setUp() {
        columnUsageTracker = new ColumnUsageTrackerImpl(HEAVY_THRESHOLD);
        sqlAstAnalysisService = new SqlAstAnalysisServiceImpl(columnUsageTracker, HEAVY_THRESHOLD);
        migrationAnalysisService = new MigrationAnalysisServiceImpl(sqlAstAnalysisService, columnUsageTracker);
        metricsService = new MetricsServiceImpl(columnUsageTracker);
    }

    @Test
    @DisplayName("Full Pipeline: Ingest AST queries -> verify column telemetry -> analyze breaking migration")
    void shouldTrackASTQueriesAndFlagBreakingColumnDrop() {
        // Step 1: Execute simulated application queries with AST analysis
        String query1 = "SELECT u.id, u.email, u.name FROM users u WHERE u.status = 'ACTIVE';";
        String query2 = "SELECT o.order_id, o.total, u.email FROM orders o JOIN users u ON o.user_id = u.id WHERE o.status = 'COMPLETED';";
        String query3 = "UPDATE users SET last_login = NOW() WHERE email = 'alice@example.com';";

        // Record each query multiple times
        for (int i = 0; i < 3; i++) {
            columnUsageTracker.recordQueryColumns(query1);
            columnUsageTracker.recordQueryColumns(query2);
        }
        columnUsageTracker.recordQueryColumns(query3);

        // Verify column access counts
        // email: 3 (query1) + 3 (query2) + 1 (query3) = 7 (exceeds threshold 5)
        assertEquals(7, columnUsageTracker.getQueryCount("users", "email"));
        assertTrue(columnUsageTracker.isColumnHeavilyQueried("users", "email"));

        // orders.total: 3 queries (below threshold 5)
        assertEquals(3, columnUsageTracker.getQueryCount("orders", "total"));
        assertFalse(columnUsageTracker.isColumnHeavilyQueried("orders", "total"));

        // Step 2: Analyze a migration script that drops the heavily queried column 'email'
        String migrationSql = """
                -- Migration V12: Remove legacy columns
                ALTER TABLE users DROP COLUMN email;
                ALTER TABLE orders DROP COLUMN total;
                ALTER TABLE users ADD COLUMN phone_number VARCHAR(50);
                """;

        MigrationAnalysisRequest request = new MigrationAnalysisRequest(migrationSql, HEAVY_THRESHOLD);
        MigrationAnalysisResponse response = migrationAnalysisService.analyzeMigration(request);

        // Verify response risk aggregation
        assertNotNull(response);
        assertEquals(3, response.getTotalRisks());
        assertEquals(2, response.getHighRisks());
        assertEquals(1, response.getLowRisks());
        assertTrue(response.isHasBreakingRisks());
        assertEquals(1, response.getHeavilyQueriedCount());

        // Verify specific finding details for the heavily queried column drop
        MigrationFindingDto emailFinding = response.getFindings().stream()
                .filter(f -> "users".equalsIgnoreCase(f.getTable()) && "email".equalsIgnoreCase(f.getColumn()))
                .findFirst()
                .orElse(null);

        assertNotNull(emailFinding);
        assertEquals("DROPPED_HEAVILY_QUERIED_COLUMN", emailFinding.getRule());
        assertEquals("high", emailFinding.getLevel());
        assertEquals("users", emailFinding.getTable());
        assertEquals("email", emailFinding.getColumn());
        assertEquals(7L, emailFinding.getQueryCount());
        assertNotNull(emailFinding.getSuggestedFix());
        assertTrue(emailFinding.getSuggestedFix().contains("Expand-Contract"));
        assertTrue(emailFinding.getMessage().contains("7 recent queries"));

        // Verify finding for total column (dropped but below heavy threshold)
        MigrationFindingDto totalFinding = response.getFindings().stream()
                .filter(f -> "orders".equalsIgnoreCase(f.getTable()) && "total".equalsIgnoreCase(f.getColumn()))
                .findFirst()
                .orElse(null);

        assertNotNull(totalFinding);
        assertEquals("DROPPED_QUERIED_COLUMN", totalFinding.getRule());
        assertEquals(3L, totalFinding.getQueryCount());
    }

    @Test
    @DisplayName("Telemetry Ingestion via MetricsService updates ColumnUsageTracker and flags risks")
    void shouldIngestViaMetricsServiceAndDetectRisks() {
        // Ingest telemetry via MetricsService
        metricsService.recordExecution(true, "accounts", "SELECT", null, null, List.of("account_number", "balance"));
        for (int i = 0; i < 10; i++) {
            metricsService.recordExecution(true, "accounts", "SELECT", null, null, List.of("balance"));
        }

        assertEquals(1, columnUsageTracker.getQueryCount("accounts", "account_number"));
        assertEquals(11, columnUsageTracker.getQueryCount("accounts", "balance"));

        // Analyze migration script
        String migrationSql = "ALTER TABLE accounts DROP COLUMN balance;";
        MigrationAnalysisResponse response = migrationAnalysisService.analyzeMigration(new MigrationAnalysisRequest(migrationSql));

        assertEquals(1, response.getHighRisks());
        assertEquals(1, response.getHeavilyQueriedCount());
        MigrationFindingDto finding = response.getFindings().get(0);
        assertEquals("DROPPED_HEAVILY_QUERIED_COLUMN", finding.getRule());
        assertEquals("balance", finding.getColumn());
        assertEquals(11L, finding.getQueryCount());
    }

    @Test
    @DisplayName("Batch telemetry ingestion supports cross-table risk evaluation")
    void shouldEvaluateRisksWithBatchTelemetry() {
        columnUsageTracker.recordBatchUsage(Map.of(
                "payments", Map.of("card_token", 50L, "currency", 2L),
                "merchants", Map.of("tax_id", 15L)
        ));

        String migrationSql = """
                ALTER TABLE payments DROP COLUMN card_token;
                ALTER TABLE payments DROP COLUMN currency;
                ALTER TABLE merchants DROP COLUMN tax_id;
                """;

        MigrationAnalysisResponse response = migrationAnalysisService.analyzeMigration(
                new MigrationAnalysisRequest(migrationSql, 10));

        assertEquals(3, response.getTotalRisks());
        assertEquals(3, response.getHighRisks());
        assertEquals(2, response.getHeavilyQueriedCount());

        List<String> rules = response.getFindings().stream().map(MigrationFindingDto::getRule).toList();
        assertTrue(rules.contains("DROPPED_HEAVILY_QUERIED_COLUMN"));
        assertTrue(rules.contains("DROPPED_QUERIED_COLUMN"));
    }

    @Test
    @DisplayName("Demo baseline seeding works out-of-the-box for interactive demo")
    void shouldSeedAndDetectDemoBaselineRisks() {
        ColumnUsageTrackerImpl demoTracker = new ColumnUsageTrackerImpl(10, true);
        demoTracker.initDemoBaseline();

        assertTrue(demoTracker.isColumnHeavilyQueried("customers", "email"));
        assertTrue(demoTracker.isColumnHeavilyQueried("orders", "amount"));

        SqlAstAnalysisServiceImpl astService = new SqlAstAnalysisServiceImpl(demoTracker, 10);
        MigrationAnalysisServiceImpl analysisService = new MigrationAnalysisServiceImpl(astService, demoTracker);

        String script = "ALTER TABLE customers DROP COLUMN email;";
        MigrationAnalysisResponse res = analysisService.analyzeMigration(new MigrationAnalysisRequest(script));

        assertEquals(1, res.getHighRisks());
        assertEquals(1, res.getHeavilyQueriedCount());
        assertEquals("DROPPED_HEAVILY_QUERIED_COLUMN", res.getFindings().get(0).getRule());
        assertEquals("customers", res.getFindings().get(0).getTable());
        assertEquals("email", res.getFindings().get(0).getColumn());
    }
}
