package com.shadowbase.backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shadowbase.backend.dto.DroppedColumnTarget;
import com.shadowbase.backend.dto.MigrationFindingDto;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.alter.AlterOperation;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link SqlAstAnalysisService} leveraging JSqlParser
 * to inspect SQL migration scripts for breaking column drop operations.
 */
@Slf4j
@Service
public class SqlAstAnalysisServiceImpl implements SqlAstAnalysisService {

    private static final Set<String> IGNORED_SPECIFIERS = Set.of(
            "COLUMN", "COLUMNS", "IF", "EXISTS", "CASCADE", "RESTRICT",
            "KEY", "CONSTRAINT", "FOREIGN", "PRIMARY", "INDEX"
    );

    private final ColumnUsageTracker columnUsageTracker;
    private final int defaultThreshold;

    public SqlAstAnalysisServiceImpl(
            ColumnUsageTracker columnUsageTracker,
            @Value("${shadowbase.analysis.heavy-query-threshold:10}") int defaultThreshold) {
        this.columnUsageTracker = columnUsageTracker;
        this.defaultThreshold = defaultThreshold;
    }

    @Override
    public List<DroppedColumnTarget> extractDroppedColumns(String sql) {
        if (sql == null || sql.isBlank()) {
            return Collections.emptyList();
        }

        List<DroppedColumnTarget> results = new ArrayList<>();
        List<String> rawStatements = splitSqlStatements(sql);

        int currentLine = 1;
        for (String rawStmt : rawStatements) {
            String trimmed = rawStmt.trim();
            if (trimmed.isEmpty()) {
                currentLine += countLines(rawStmt);
                continue;
            }

            try {
                Statements statements = CCJSqlParserUtil.parseStatements(trimmed);
                for (Statement stmt : statements.getStatements()) {
                    if (stmt instanceof Alter alter) {
                        extractFromAlter(alter, currentLine, trimmed, results);
                    }
                }
            } catch (Exception parseEx) {
                // Graceful fallback for statements with non-standard syntax
                log.debug("JSqlParser could not parse statement at line {}: {}", currentLine, parseEx.getMessage());
            }

            currentLine += countLines(rawStmt);
        }

        return results;
    }

    @Override
    public List<MigrationFindingDto> analyzeColumnDropRisks(String sql, int threshold) {
        int effectiveThreshold = (threshold > 0) ? threshold : this.defaultThreshold;
        List<DroppedColumnTarget> droppedTargets = extractDroppedColumns(sql);

        if (droppedTargets.isEmpty()) {
            return Collections.emptyList();
        }

        List<MigrationFindingDto> findings = new ArrayList<>();

        for (DroppedColumnTarget target : droppedTargets) {
            String table = target.getTable();
            String column = target.getColumn();
            long queryCount = columnUsageTracker.getQueryCount(table, column);

            if (queryCount >= effectiveThreshold) {
                // Critical risk: heavily queried column being dropped
                findings.add(MigrationFindingDto.builder()
                        .level("high")
                        .rule("DROPPED_HEAVILY_QUERIED_COLUMN")
                        .message(String.format(
                                "CRITICAL: Dropping heavily queried column '%s' on table '%s' (%d recent queries). This will immediately break active application traffic.",
                                column, table, queryCount))
                        .line(target.getLine())
                        .statement(truncateStatement(target.getStatement()))
                        .table(table)
                        .column(column)
                        .suggestedFix("Apply the Expand-Contract pattern: stop querying this column in active code, deploy changes, and verify before executing DROP COLUMN.")
                        .queryCount(queryCount)
                        .build());
            } else if (queryCount > 0) {
                // Moderate warning: column has active queries, but under heavy threshold
                findings.add(MigrationFindingDto.builder()
                        .level("high")
                        .rule("DROPPED_QUERIED_COLUMN")
                        .message(String.format(
                                "Warning: Dropping active column '%s' on table '%s' (%d recent queries).",
                                column, table, queryCount))
                        .line(target.getLine())
                        .statement(truncateStatement(target.getStatement()))
                        .table(table)
                        .column(column)
                        .suggestedFix("Verify no application queries or background services reference this column before executing DROP COLUMN.")
                        .queryCount(queryCount)
                        .build());
            }
        }

        return findings;
    }

    private void extractFromAlter(Alter alter, int line, String rawStatement, List<DroppedColumnTarget> results) {
        if (alter.getTable() == null || alter.getAlterExpressions() == null) {
            return;
        }

        String tableName = cleanIdentifier(alter.getTable().getName());

        for (AlterExpression expr : alter.getAlterExpressions()) {
            if (expr.getOperation() == AlterOperation.DROP) {
                // Single column name in AlterExpression
                String colName = expr.getColumnName();
                if (colName != null && !colName.isBlank()) {
                    String cleanCol = cleanIdentifier(colName);
                    if (!isIgnored(cleanCol)) {
                        results.add(DroppedColumnTarget.builder()
                                .table(tableName)
                                .column(cleanCol)
                                .line(line)
                                .statement(rawStatement)
                                .build());
                    }
                }

                // Check parameters (handles DROP COLUMN col1, col2 or DROP (col1, col2))
                if (expr.getParameters() != null) {
                    for (String param : expr.getParameters()) {
                        String cleanParam = cleanIdentifier(param);
                        if (!cleanParam.isBlank() && !isIgnored(cleanParam)) {
                            results.add(DroppedColumnTarget.builder()
                                    .table(tableName)
                                    .column(cleanParam)
                                    .line(line)
                                    .statement(rawStatement)
                                    .build());
                        }
                    }
                }
            }
        }
    }

    private boolean isIgnored(String token) {
        return token == null || token.isBlank() || IGNORED_SPECIFIERS.contains(token.toUpperCase(Locale.ROOT));
    }

    private String cleanIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        String cleaned = identifier.replaceAll("[`\"\\[\\];,()]", "").trim();
        if (cleaned.contains(".")) {
            cleaned = cleaned.substring(cleaned.lastIndexOf('.') + 1).trim();
        }
        return cleaned;
    }

    private String truncateStatement(String statement) {
        if (statement == null) {
            return "";
        }
        return statement.length() > 100 ? statement.substring(0, 100) + "..." : statement;
    }

    private List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                current.append(ch);
                if (ch == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                current.append(ch);
                if (ch == '*' && next == '/') {
                    current.append('/');
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (ch == '-' && next == '-') {
                current.append("--");
                inLineComment = true;
                i++;
                continue;
            }
            if (ch == '/' && next == '*') {
                current.append("/*");
                inBlockComment = true;
                i++;
                continue;
            }
            if (ch == '\'' || ch == '"' || ch == '`') {
                inQuote = !inQuote;
                current.append(ch);
                continue;
            }
            if (ch == ';' && !inQuote) {
                current.append(';');
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }

        if (!current.toString().trim().isEmpty()) {
            statements.add(current.toString());
        }

        return statements;
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
