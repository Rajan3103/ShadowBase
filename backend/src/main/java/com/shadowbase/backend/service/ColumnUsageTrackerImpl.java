package com.shadowbase.backend.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe in-memory implementation of {@link ColumnUsageTracker}.
 * Records column query frequencies to provide telemetry for AST risk analysis.
 */
@Slf4j
@Service
public class ColumnUsageTrackerImpl implements ColumnUsageTracker {

    private final int defaultThreshold;

    // Key format: "table_name.column_name" (normalized to lowercase)
    private final ConcurrentHashMap<String, AtomicLong> columnUsageStats = new ConcurrentHashMap<>();

    public ColumnUsageTrackerImpl(
            @Value("${shadowbase.analysis.heavy-query-threshold:10}") int defaultThreshold) {
        this.defaultThreshold = defaultThreshold;
    }

    @Override
    public void recordColumnAccess(String table, String column) {
        recordColumnAccess(table, column, 1L);
    }

    @Override
    public void recordColumnAccess(String table, String column, long count) {
        if (table == null || column == null || count <= 0) {
            return;
        }
        String key = normalizeKey(table, column);
        if (key == null) {
            return;
        }
        columnUsageStats.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(count);
        log.trace("Recorded {} access(es) on column '{}'", count, key);
    }

    @Override
    public void recordQueryColumns(String sql) {
        if (sql == null || sql.isBlank()) {
            return;
        }
        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (stmt instanceof Select select) {
                processSelect(select);
            } else if (stmt instanceof Update update) {
                processUpdate(update);
            } else if (stmt instanceof Insert insert) {
                processInsert(insert);
            } else if (stmt instanceof Delete delete) {
                processDelete(delete);
            }
        } catch (Exception e) {
            log.debug("Skipping column usage AST parsing for statement: {}", e.getMessage());
        }
    }

    private class ColumnFinder extends ExpressionVisitorAdapter {
        private final String defaultTable;
        private final Map<String, String> aliasToTable;
        private final Set<String> accessedColumns;

        ColumnFinder(String defaultTable, Map<String, String> aliasToTable, Set<String> accessedColumns) {
            this.defaultTable = defaultTable;
            this.aliasToTable = aliasToTable;
            this.accessedColumns = accessedColumns;
        }

        @Override
        public void visit(Column column) {
            String rawTbl = (column.getTable() != null && column.getTable().getName() != null)
                    ? cleanName(column.getTable().getName())
                    : defaultTable;
            String resolvedTable = aliasToTable.getOrDefault(rawTbl.toLowerCase(Locale.ROOT), rawTbl);
            String colName = cleanName(column.getColumnName());
            if (!resolvedTable.isBlank() && !colName.isBlank() && !"*".equals(colName)) {
                accessedColumns.add(resolvedTable + "." + colName);
            }
        }
    }

    private void processSelect(Select select) {
        if (select.getPlainSelect() == null) {
            return;
        }
        PlainSelect plain = select.getPlainSelect();
        Map<String, String> aliasToTable = new HashMap<>();

        String defaultTable = "unknown";
        if (plain.getFromItem() instanceof Table t) {
            defaultTable = cleanName(t.getName());
            aliasToTable.put(defaultTable.toLowerCase(Locale.ROOT), defaultTable);
            if (t.getAlias() != null && t.getAlias().getName() != null) {
                aliasToTable.put(cleanName(t.getAlias().getName()).toLowerCase(Locale.ROOT), defaultTable);
            }
        } else if (plain.getFromItem() != null) {
            defaultTable = cleanName(plain.getFromItem().toString());
        }

        if (plain.getJoins() != null) {
            for (Join join : plain.getJoins()) {
                if (join.getRightItem() instanceof Table jt) {
                    String joinTable = cleanName(jt.getName());
                    aliasToTable.put(joinTable.toLowerCase(Locale.ROOT), joinTable);
                    if (jt.getAlias() != null && jt.getAlias().getName() != null) {
                        aliasToTable.put(cleanName(jt.getAlias().getName()).toLowerCase(Locale.ROOT), joinTable);
                    }
                }
            }
        }

        Set<String> accessedColumns = new HashSet<>();
        ColumnFinder finder = new ColumnFinder(defaultTable, aliasToTable, accessedColumns);

        if (plain.getSelectItems() != null) {
            for (SelectItem<?> item : plain.getSelectItems()) {
                if (item.getExpression() != null) {
                    item.getExpression().accept(finder);
                }
            }
        }

        if (plain.getWhere() != null) {
            plain.getWhere().accept(finder);
        }

        if (plain.getJoins() != null) {
            for (Join join : plain.getJoins()) {
                if (join.getOnExpressions() != null) {
                    for (Expression onExpr : join.getOnExpressions()) {
                        onExpr.accept(finder);
                    }
                }
            }
        }

        if (plain.getHaving() != null) {
            plain.getHaving().accept(finder);
        }

        recordUniqueColumns(accessedColumns);
    }

    private void processUpdate(Update update) {
        String table = update.getTable() != null ? cleanName(update.getTable().getName()) : "unknown";
        Map<String, String> aliasToTable = Collections.singletonMap(table.toLowerCase(Locale.ROOT), table);
        Set<String> accessedColumns = new HashSet<>();
        ColumnFinder finder = new ColumnFinder(table, aliasToTable, accessedColumns);

        if (update.getUpdateSets() != null) {
            for (UpdateSet set : update.getUpdateSets()) {
                if (set.getColumns() != null) {
                    for (Column col : set.getColumns()) {
                        finder.visit(col);
                    }
                }
                if (set.getValues() != null) {
                    for (Expression valExpr : set.getValues()) {
                        valExpr.accept(finder);
                    }
                }
            }
        }
        if (update.getWhere() != null) {
            update.getWhere().accept(finder);
        }

        recordUniqueColumns(accessedColumns);
    }

    private void processInsert(Insert insert) {
        String table = insert.getTable() != null ? cleanName(insert.getTable().getName()) : "unknown";
        if (insert.getColumns() != null) {
            for (Column col : insert.getColumns()) {
                recordColumnAccess(table, cleanName(col.getColumnName()));
            }
        }
    }

    private void processDelete(Delete delete) {
        String table = delete.getTable() != null ? cleanName(delete.getTable().getName()) : "unknown";
        if (delete.getWhere() != null) {
            Map<String, String> aliasToTable = Collections.singletonMap(table.toLowerCase(Locale.ROOT), table);
            Set<String> accessedColumns = new HashSet<>();
            delete.getWhere().accept(new ColumnFinder(table, aliasToTable, accessedColumns));
            recordUniqueColumns(accessedColumns);
        }
    }

    private void recordUniqueColumns(Set<String> accessedColumns) {
        for (String colKey : accessedColumns) {
            int dotIdx = colKey.indexOf('.');
            if (dotIdx > 0 && dotIdx < colKey.length() - 1) {
                recordColumnAccess(colKey.substring(0, dotIdx), colKey.substring(dotIdx + 1));
            }
        }
    }

    private String extractTableFromItem(FromItem fromItem) {
        if (fromItem instanceof Table table) {
            return cleanName(table.getName());
        } else if (fromItem != null) {
            return cleanName(fromItem.toString());
        }
        return "unknown";
    }

    @Override
    public long getQueryCount(String table, String column) {
        String key = normalizeKey(table, column);
        if (key == null) {
            return 0L;
        }
        AtomicLong counter = columnUsageStats.get(key);
        return counter != null ? counter.get() : 0L;
    }

    @Override
    public boolean isColumnHeavilyQueried(String table, String column) {
        return isColumnHeavilyQueried(table, column, this.defaultThreshold);
    }

    @Override
    public boolean isColumnHeavilyQueried(String table, String column, int threshold) {
        return getQueryCount(table, column) >= threshold;
    }

    @Override
    public Map<String, Long> getTableColumnUsage(String table) {
        if (table == null || table.isBlank()) {
            return Collections.emptyMap();
        }
        String prefix = cleanName(table).toLowerCase(Locale.ROOT) + ".";
        Map<String, Long> result = new HashMap<>();

        for (Map.Entry<String, AtomicLong> entry : columnUsageStats.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String column = entry.getKey().substring(prefix.length());
                result.put(column, entry.getValue().get());
            }
        }
        return result;
    }

    @Override
    public void reset() {
        columnUsageStats.clear();
        log.info("Reset column query usage stats.");
    }

    private String normalizeKey(String table, String column) {
        String cleanTable = cleanName(table);
        String cleanColumn = cleanName(column);
        if (cleanTable.isBlank() || cleanColumn.isBlank() || "unknown".equalsIgnoreCase(cleanTable)) {
            return null;
        }
        return cleanTable.toLowerCase(Locale.ROOT) + "." + cleanColumn.toLowerCase(Locale.ROOT);
    }

    private String cleanName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[`\"\\[\\];,]", "").trim();
    }
}
