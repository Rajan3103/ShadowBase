package com.shadowbase.backend.service.seeding;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.shadowbase.backend.Exception.DataSeedingException;

import lombok.extern.slf4j.Slf4j;

/**
 * Component responsible for analyzing foreign key relationships between tables
 * and determining a topologically sorted insertion order (parent tables before child tables).
 * <p>
 * Detects circular foreign key dependencies and fails preflight validation with details
 * to prevent deadlocks or foreign key constraint violations during seeding.
 */
@Slf4j
@Component
public class TableDependencyPlanner {

    /**
     * Determines a safe, topologically sorted insertion order for the requested tables.
     *
     * @param connection active JDBC connection (source or target)
     * @param tables     list of tables planned for data transfer
     * @return topologically ordered list of table names
     * @throws DataSeedingException if a circular dependency is detected
     * @throws SQLException         if metadata queries fail
     */
    public List<String> planDependencyOrder(Connection connection, List<String> tables) throws SQLException {
        if (tables == null || tables.isEmpty()) {
            return Collections.emptyList();
        }

        // Canonical mapping for case-insensitive lookup
        Map<String, String> canonicalNames = new HashMap<>();
        for (String t : tables) {
            canonicalNames.put(t.toLowerCase(), t);
        }

        // Graph representation: parentTable -> set of childTables
        Map<String, Set<String>> adjacencyList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (String t : tables) {
            adjacencyList.put(canonicalNames.get(t.toLowerCase()), new HashSet<>());
            inDegree.put(canonicalNames.get(t.toLowerCase()), 0);
        }

        DatabaseMetaData meta = connection.getMetaData();

        for (String childTable : tables) {
            String canonicalChild = canonicalNames.get(childTable.toLowerCase());

            // Query foreign keys imported by this child table
            try (ResultSet rs = meta.getImportedKeys(null, null, childTable)) {
                processImportedKeys(rs, canonicalChild, canonicalNames, adjacencyList, inDegree);
            } catch (SQLException e) {
                // Retry with lowercase in case database metadata requires exact matching
                try (ResultSet rs = meta.getImportedKeys(null, null, childTable.toLowerCase())) {
                    processImportedKeys(rs, canonicalChild, canonicalNames, adjacencyList, inDegree);
                } catch (SQLException ignored) {
                }
            }
        }

        // Kahn's algorithm for topological sorting
        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sortedOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sortedOrder.add(current);

            for (String neighbor : adjacencyList.getOrDefault(current, Collections.emptySet())) {
                int updatedInDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, updatedInDegree);
                if (updatedInDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // Cycle check: if not all tables were sorted, a cycle exists
        if (sortedOrder.size() < tables.size()) {
            List<String> cyclicTables = inDegree.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .toList();

            log.error("Circular foreign key dependency detected among tables: {}", cyclicTables);
            throw new DataSeedingException(String.format(
                    "Circular foreign key dependency detected involving tables: %s. "
                    + "Data seeding cannot proceed safely without resolving or deferring constraints.",
                    cyclicTables
            ));
        }

        log.info("Determined table insertion order: {}", sortedOrder);
        return sortedOrder;
    }

    private void processImportedKeys(ResultSet rs,
                                    String canonicalChild,
                                    Map<String, String> canonicalNames,
                                    Map<String, Set<String>> adjacencyList,
                                    Map<String, Integer> inDegree) throws SQLException {
        while (rs.next()) {
            String parentTable = rs.getString("PKTABLE_NAME");
            if (parentTable == null) {
                continue;
            }

            String canonicalParent = canonicalNames.get(parentTable.toLowerCase());
            // Ignore if parent is not in the planned transfer set or is self-referential
            if (canonicalParent == null || canonicalParent.equalsIgnoreCase(canonicalChild)) {
                continue;
            }

            // Edge: parent -> child
            Set<String> children = adjacencyList.get(canonicalParent);
            if (children != null && children.add(canonicalChild)) {
                inDegree.put(canonicalChild, inDegree.get(canonicalChild) + 1);
            }
        }
    }
}
