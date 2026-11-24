package com.shadowbase.backend.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
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
            }
        } catch (Exception e) {
            log.debug("Skipping column usage AST parsing for statement: {}", e.getMessage());
        }
    }

    private void processSelect(Select select) {
        if (select.getPlainSelect() == null) {
            return;
        }
        PlainSelect plain = select.getPlainSelect();
        String defaultTable = extractTableFromItem(plain.getFromItem());

        if (plain.getSelectItems() != null) {
            for (SelectItem<?> item : plain.getSelectItems()) {
                if (item.getExpression() instanceof Column col) {
                    String tbl = col.getTable() != null ? cleanName(col.getTable().getName()) : defaultTable;
                    recordColumnAccess(tbl, cleanName(col.getColumnName()));
                }
            }
        }
    }

    private void processUpdate(Update update) {
        String table = update.getTable() != null ? cleanName(update.getTable().getName()) : "unknown";
        if (update.getUpdateSets() != null) {
            for (UpdateSet set : update.getUpdateSets()) {
                if (set.getColumns() != null) {
                    for (Column col : set.getColumns()) {
                        recordColumnAccess(table, cleanName(col.getColumnName()));
                    }
                }
            }
        }
    }

    private void processInsert(Insert insert) {
        String table = insert.getTable() != null ? cleanName(insert.getTable().getName()) : "unknown";
        if (insert.getColumns() != null) {
            for (Column col : insert.getColumns()) {
                recordColumnAccess(table, cleanName(col.getColumnName()));
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
