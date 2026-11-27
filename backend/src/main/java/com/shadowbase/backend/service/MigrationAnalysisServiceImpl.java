package com.shadowbase.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.shadowbase.backend.dto.MigrationAnalysisRequest;
import com.shadowbase.backend.dto.MigrationAnalysisResponse;
import com.shadowbase.backend.dto.MigrationFindingDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link MigrationAnalysisService}.
 * Inspects SQL migration statements to identify breaking operations,
 * table locks, and backward-incompatible schema changes.
 */
@Slf4j
@Service
public class MigrationAnalysisServiceImpl implements MigrationAnalysisService {

    private final SqlAstAnalysisService sqlAstAnalysisService;
    private final ColumnUsageTracker columnUsageTracker;

    public MigrationAnalysisServiceImpl() {
        this(null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MigrationAnalysisServiceImpl(
            @org.springframework.beans.factory.annotation.Autowired(required = false) SqlAstAnalysisService sqlAstAnalysisService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ColumnUsageTracker columnUsageTracker) {
        this.sqlAstAnalysisService = sqlAstAnalysisService;
        this.columnUsageTracker = columnUsageTracker;
    }

    private static final String IDENTIFIER = "((?:[`\"\\[]?[\\w$]+[`\"\\]]?\\.)?[`\"\\[]?[\\w$]+[`\"\\]]?)";

    private static final Pattern DROP_TABLE_PATTERN =
            Pattern.compile("(?i)^DROP\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?" + IDENTIFIER);

    private static final Pattern TRUNCATE_PATTERN =
            Pattern.compile("(?i)^TRUNCATE\\s+(?:TABLE\\s+)?" + IDENTIFIER);

    private static final Pattern RENAME_TABLE_DIRECT_PATTERN =
            Pattern.compile("(?i)^RENAME\\s+TABLE\\s+" + IDENTIFIER + "\\s+TO\\s+" + IDENTIFIER);

    private static final Pattern ALTER_TABLE_PATTERN =
            Pattern.compile("(?i)^ALTER\\s+TABLE\\s+(?:IF\\s+EXISTS\\s+)?" + IDENTIFIER + "\\s+(.+)");

    private static final Pattern CREATE_TABLE_PATTERN =
            Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" + IDENTIFIER);

    private static final Pattern CREATE_INDEX_PATTERN =
            Pattern.compile("(?i)^CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+.*?\\s+ON\\s+" + IDENTIFIER);

    private static final Pattern DROP_INDEX_PATTERN =
            Pattern.compile("(?i)^DROP\\s+INDEX\\s+(?:IF\\s+EXISTS\\s+)?" + IDENTIFIER + "(?:\\s+ON\\s+" + IDENTIFIER + ")?");

    @Override
    public MigrationAnalysisResponse analyzeMigration(MigrationAnalysisRequest request) {
        if (request == null || request.getSql() == null || request.getSql().trim().isEmpty()) {
            return MigrationAnalysisResponse.builder()
                    .totalRisks(0)
                    .highRisks(0)
                    .mediumRisks(0)
                    .lowRisks(0)
                    .hasBreakingRisks(false)
                    .heavilyQueriedCount(0)
                    .findings(new ArrayList<>())
                    .build();
        }

        String rawSql = request.getSql();
        int threshold = (request.getHeavilyQueriedThreshold() != null && request.getHeavilyQueriedThreshold() > 0)
                ? request.getHeavilyQueriedThreshold()
                : 10;

        List<ParsedStatement> statements = splitStatements(rawSql);
        List<MigrationFindingDto> findings = new ArrayList<>();

        int highCount = 0;
        int mediumCount = 0;
        int lowCount = 0;

        for (ParsedStatement stmt : statements) {
            String text = stmt.text.trim();
            int line = stmt.line;

            Matcher m;

            // 1. DROP TABLE
            if ((m = DROP_TABLE_PATTERN.matcher(text)).find()) {
                String table = cleanName(m.group(1));
                findings.add(buildFinding("high", "DROP_TABLE",
                        String.format("Dropping table '%s' will break every live query that accesses it.", table),
                        line, text, table));
                highCount++;
                continue;
            }

            // 2. TRUNCATE TABLE
            if ((m = TRUNCATE_PATTERN.matcher(text)).find()) {
                String table = cleanName(m.group(1));
                findings.add(buildFinding("high", "TRUNCATE_TABLE",
                        String.format("Truncating table '%s' permanently deletes all data and locks the table.", table),
                        line, text, table));
                highCount++;
                continue;
            }

            // 3. RENAME TABLE (MySQL RENAME TABLE t1 TO t2)
            if ((m = RENAME_TABLE_DIRECT_PATTERN.matcher(text)).find()) {
                String oldTable = cleanName(m.group(1));
                String newTable = cleanName(m.group(2));
                findings.add(buildFinding("high", "RENAME_TABLE",
                        String.format("Renaming table '%s' to '%s' will break every live query using the old name.", oldTable, newTable),
                        line, text, oldTable));
                highCount++;
                continue;
            }

            // 4. CREATE TABLE
            if ((m = CREATE_TABLE_PATTERN.matcher(text)).find()) {
                String table = cleanName(m.group(1));
                findings.add(buildFinding("low", "CREATE_TABLE",
                        String.format("Creating table '%s' is an additive, non-breaking schema change.", table),
                        line, text, table));
                lowCount++;
                continue;
            }

            // 5. CREATE INDEX
            if ((m = CREATE_INDEX_PATTERN.matcher(text)).find()) {
                String table = cleanName(m.group(1));
                findings.add(buildFinding("low", "CREATE_INDEX",
                        String.format("Creating an index on '%s' optimizes query paths without altering table columns.", table),
                        line, text, table));
                lowCount++;
                continue;
            }

            // 6. DROP INDEX
            if ((m = DROP_INDEX_PATTERN.matcher(text)).find()) {
                String indexName = cleanName(m.group(1));
                String table = (m.groupCount() >= 2 && m.group(2) != null) ? cleanName(m.group(2)) : indexName;
                findings.add(buildFinding("medium", "DROP_INDEX",
                        String.format("Dropping index '%s' may degrade query performance by triggering full table scans.", indexName),
                        line, text, table));
                mediumCount++;
                continue;
            }

            // 7. ALTER TABLE clauses
            if ((m = ALTER_TABLE_PATTERN.matcher(text)).find()) {
                String table = cleanName(m.group(1));
                String clauses = m.group(2);
                List<String> splitClauses = splitTopLevelCommas(clauses);

                for (String clause : splitClauses) {
                    clause = clause.trim();
                    if (clause.isEmpty()) continue;

                    MigrationFindingDto finding = analyzeAlterClause(clause, table, line, text, threshold);
                    if (finding != null) {
                        findings.add(finding);
                        if ("high".equalsIgnoreCase(finding.getLevel())) highCount++;
                        else if ("medium".equalsIgnoreCase(finding.getLevel())) mediumCount++;
                        else lowCount++;
                    }
                }
            }
        }

        // 8. AST Analysis (JSqlParser) for multi-column drops or AST-specific syntax
        if (sqlAstAnalysisService != null) {
            List<MigrationFindingDto> astFindings = sqlAstAnalysisService.analyzeColumnDropRisks(rawSql, threshold);
            for (MigrationFindingDto astFinding : astFindings) {
                boolean alreadyCovered = findings.stream().anyMatch(f ->
                        f.getTable().equalsIgnoreCase(astFinding.getTable()) &&
                        (f.getRule().equals(astFinding.getRule()) || f.getRule().startsWith("DROPPED_")));
                if (!alreadyCovered) {
                    findings.add(astFinding);
                    if ("high".equalsIgnoreCase(astFinding.getLevel())) highCount++;
                    else if ("medium".equalsIgnoreCase(astFinding.getLevel())) mediumCount++;
                    else lowCount++;
                }
            }
        }

        int heavilyQueriedCount = 0;
        for (MigrationFindingDto finding : findings) {
            if (finding.getRule() != null && finding.getRule().contains("HEAVILY_QUERIED")) {
                heavilyQueriedCount++;
            }
        }

        return MigrationAnalysisResponse.builder()
                .totalRisks(findings.size())
                .highRisks(highCount)
                .mediumRisks(mediumCount)
                .lowRisks(lowCount)
                .hasBreakingRisks(highCount > 0)
                .heavilyQueriedCount(heavilyQueriedCount)
                .findings(findings)
                .build();
    }

    private MigrationFindingDto analyzeAlterClause(String clause, String table, int line, String fullStatement, int threshold) {
        Matcher m;

        // HIGH: DROP COLUMN
        Pattern dropCol = Pattern.compile("(?i)^DROP\\s+(?:COLUMN\\s+)?(?:IF\\s+EXISTS\\s+)?" + IDENTIFIER);
        if ((m = dropCol.matcher(clause)).find()) {
            String col = cleanName(m.group(1));

            // Cross-reference with live column query usage telemetry
            if (columnUsageTracker != null && columnUsageTracker.isColumnHeavilyQueried(table, col, threshold)) {
                long count = columnUsageTracker.getQueryCount(table, col);
                return buildFinding("high", "DROPPED_HEAVILY_QUERIED_COLUMN",
                        String.format("CRITICAL: Dropping heavily queried column '%s' on table '%s' (%d recent queries). This will immediately break active application traffic.", col, table, count),
                        line, fullStatement, table, col,
                        "Apply Expand-Contract pattern: stop querying this column in active code, deploy, and verify before executing DROP.", count);
            }
            if (columnUsageTracker != null && columnUsageTracker.getQueryCount(table, col) > 0) {
                long count = columnUsageTracker.getQueryCount(table, col);
                return buildFinding("high", "DROPPED_QUERIED_COLUMN",
                        String.format("Warning: Dropping active column '%s' on table '%s' (%d recent queries).", col, table, count),
                        line, fullStatement, table, col,
                        "Verify no application services reference this column before executing DROP.", count);
            }

            return buildFinding("high", "DROP_COLUMN",
                    String.format("Dropping column '%s' from '%s' will break any live query or insert that still uses it.", col, table),
                    line, fullStatement, table, col,
                    "Verify all application queries have been updated before dropping this column.", null);
        }

        // HIGH: RENAME COLUMN
        Pattern renameCol = Pattern.compile("(?i)^RENAME\\s+(?:COLUMN\\s+)?" + IDENTIFIER + "\\s+TO\\s+" + IDENTIFIER);
        if ((m = renameCol.matcher(clause)).find()) {
            String oldCol = cleanName(m.group(1));
            String newCol = cleanName(m.group(2));

            if (columnUsageTracker != null && columnUsageTracker.isColumnHeavilyQueried(table, oldCol, threshold)) {
                long count = columnUsageTracker.getQueryCount(table, oldCol);
                return buildFinding("high", "RENAMED_HEAVILY_QUERIED_COLUMN",
                        String.format("CRITICAL: Renaming heavily queried column '%s' to '%s' on table '%s' (%d recent queries). Active queries will fail immediately.", oldCol, newCol, table, count),
                        line, fullStatement, table, oldCol,
                        "Apply Expand-Contract pattern: add new column '" + newCol + "', dual-write, migrate readers, then drop old column.", count);
            }

            return buildFinding("high", "RENAME_COLUMN",
                    String.format("Renaming column '%s' to '%s' in '%s' will break every live query that still uses the old name.", oldCol, newCol, table),
                    line, fullStatement, table, oldCol,
                    "Ensure all application queries are updated to use '" + newCol + "' before renaming.", null);
        }

        // HIGH: RENAME TABLE (ALTER TABLE t RENAME [TO] t2)
        Pattern renameTbl = Pattern.compile("(?i)^RENAME\\s+(?:TO|AS)\\s+" + IDENTIFIER);
        if ((m = renameTbl.matcher(clause)).find()) {
            String newTable = cleanName(m.group(1));
            return buildFinding("high", "RENAME_TABLE",
                    String.format("Renaming table '%s' to '%s' will break every live query that still uses the old name.", table, newTable),
                    line, fullStatement, table);
        }

        // MEDIUM: ALTER/MODIFY COLUMN TYPE
        Pattern changeType = Pattern.compile("(?i)^(?:ALTER|MODIFY)\\s+(?:COLUMN\\s+)?" + IDENTIFIER + "\\s+(?:SET\\s+DATA\\s+)?(?:TYPE\\s+)?\\w+");
        if ((m = changeType.matcher(clause)).find()) {
            String col = cleanName(m.group(1));
            return buildFinding("medium", "CHANGE_COLUMN_TYPE",
                    String.format("Changing the type of column '%s' in '%s' can reject values from existing callers and lock the table.", col, table),
                    line, fullStatement, table);
        }

        // MEDIUM: SET NOT NULL
        Pattern setNotNull = Pattern.compile("(?i)^(?:ALTER|MODIFY)\\s+(?:COLUMN\\s+)?" + IDENTIFIER + "\\s+SET\\s+NOT\\s+NULL");
        if ((m = setNotNull.matcher(clause)).find()) {
            String col = cleanName(m.group(1));
            return buildFinding("medium", "SET_NOT_NULL",
                    String.format("Making column '%s' in '%s' NOT NULL will reject inserts leaving it empty and fails if existing rows contain null.", col, table),
                    line, fullStatement, table);
        }

        // MEDIUM: DROP CONSTRAINT / FOREIGN KEY
        Pattern dropConstraint = Pattern.compile("(?i)^DROP\\s+(?:CONSTRAINT|FOREIGN\\s+KEY)\\s+(?:IF\\s+EXISTS\\s+)?" + IDENTIFIER);
        if ((m = dropConstraint.matcher(clause)).find()) {
            String constraint = cleanName(m.group(1));
            return buildFinding("medium", "DROP_CONSTRAINT",
                    String.format("Dropping constraint '%s' on '%s' removes data integrity enforcement.", constraint, table),
                    line, fullStatement, table);
        }

        // MEDIUM: DROP INDEX inside ALTER TABLE
        Pattern dropIdx = Pattern.compile("(?i)^DROP\\s+(?:INDEX|KEY)\\s+(?:IF\\s+EXISTS\\s+)?" + IDENTIFIER);
        if ((m = dropIdx.matcher(clause)).find()) {
            String idxName = cleanName(m.group(1));
            return buildFinding("medium", "DROP_INDEX",
                    String.format("Dropping index '%s' on '%s' may degrade query performance by causing full table scans.", idxName, table),
                    line, fullStatement, table);
        }

        // MEDIUM: ADD CONSTRAINT
        Pattern addConstraint = Pattern.compile("(?i)^ADD\\s+(?:CONSTRAINT\\s+)?(?:UNIQUE|FOREIGN\\s+KEY|PRIMARY\\s+KEY|CHECK)?\\s*" + IDENTIFIER);
        if (clause.toUpperCase().startsWith("ADD CONSTRAINT") || clause.toUpperCase().startsWith("ADD UNIQUE") ||
                clause.toUpperCase().startsWith("ADD FOREIGN KEY") || clause.toUpperCase().startsWith("ADD PRIMARY KEY") ||
                clause.toUpperCase().startsWith("ADD CHECK")) {
            return buildFinding("medium", "ADD_CONSTRAINT",
                    String.format("Adding a constraint to '%s' will reject writes breaking the new validation and fails if existing rows violate it.", table),
                    line, fullStatement, table);
        }

        // ADD COLUMN: Check NOT NULL without DEFAULT
        Pattern addCol = Pattern.compile("(?i)^ADD\\s+(?:COLUMN\\s+)?(?:IF\\s+NOT\\s+EXISTS\\s+)?" + IDENTIFIER);
        if ((m = addCol.matcher(clause)).find()) {
            String col = cleanName(m.group(1));
            boolean notNull = Pattern.compile("(?i)\\bNOT\\s+NULL\\b").matcher(clause).find();
            boolean hasDefault = Pattern.compile("(?i)\\bDEFAULT\\b").matcher(clause).find();

            if (notNull && !hasDefault) {
                return buildFinding("medium", "ADD_NOT_NULL_COLUMN",
                        String.format("Adding NOT NULL column '%s' to '%s' without a DEFAULT fails if table contains existing rows.", col, table),
                        line, fullStatement, table);
            } else {
                return buildFinding("low", "ADD_COLUMN",
                        String.format("Adding column '%s' to '%s' is safe for backward compatibility.", col, table),
                        line, fullStatement, table);
            }
        }

        return null;
    }

    private MigrationFindingDto buildFinding(String level, String rule, String message, int line, String statement, String table) {
        return buildFinding(level, rule, message, line, statement, table, null, null, null);
    }

    private MigrationFindingDto buildFinding(String level, String rule, String message, int line, String statement, String table, String column, String suggestedFix, Long queryCount) {
        return MigrationFindingDto.builder()
                .level(level)
                .rule(rule)
                .message(message)
                .line(line)
                .statement(statement.length() > 100 ? statement.substring(0, 100) + "..." : statement)
                .table(table)
                .column(column)
                .suggestedFix(suggestedFix)
                .queryCount(queryCount)
                .build();
    }

    private String cleanName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[`\"\\[\\];,]", "").trim();
    }

    private List<ParsedStatement> splitStatements(String sql) {
        List<ParsedStatement> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int line = 1;
        int stmtStartLine = 1;

        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = (i + 1 < sql.length()) ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                    line++;
                }
                continue;
            }

            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                } else if (ch == '\n') {
                    line++;
                }
                continue;
            }

            if (inQuote) {
                if (ch == '\'') {
                    if (next == '\'') {
                        i++; // escaped quote
                    } else {
                        inQuote = false;
                    }
                } else if (ch == '\n') {
                    line++;
                }
                current.append(ch);
                continue;
            }

            // Check entering comments or quotes
            if (ch == '-' && next == '-') {
                inLineComment = true;
                i++;
                continue;
            }

            if (ch == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }

            if (ch == '\'') {
                inQuote = true;
                current.append(ch);
                continue;
            }

            if (ch == '\n') {
                line++;
            }

            if (current.length() == 0 && Character.isWhitespace(ch)) {
                continue;
            }

            if (current.length() == 0) {
                stmtStartLine = line;
            }

            if (ch == ';') {
                String s = current.toString().trim();
                if (!s.isEmpty()) {
                    result.add(new ParsedStatement(s, stmtStartLine));
                }
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            result.add(new ParsedStatement(remaining, stmtStartLine));
        }

        return result;
    }

    private List<String> splitTopLevelCommas(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')') depth = Math.max(0, depth - 1);

            if (ch == ',' && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        if (current.length() > 0 && !current.toString().trim().isEmpty()) {
            parts.add(current.toString().trim());
        }

        return parts;
    }

    private record ParsedStatement(String text, int line) {}
}
