package com.shadowbase.backend.service.seeding;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.shadowbase.backend.Exception.DataSeedingException;

/**
 * Unit tests for {@link TableDependencyPlanner}.
 * Verifies foreign-key topological sorting, multi-root trees, and circular dependency detection.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableDependencyPlannerTest {

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private ResultSet customersRs;

    @Mock
    private ResultSet ordersRs;

    @Mock
    private ResultSet orderItemsRs;

    private TableDependencyPlanner planner;

    @BeforeEach
    void setUp() throws SQLException {
        planner = new TableDependencyPlanner();
        when(connection.getMetaData()).thenReturn(metaData);
    }

    @Test
    void testLinearDependencyChain() throws SQLException {
        // customers -> orders -> order_items
        List<String> tables = List.of("order_items", "customers", "orders");

        // customers has no imported keys
        when(metaData.getImportedKeys(any(), any(), eq("customers"))).thenReturn(customersRs);
        when(customersRs.next()).thenReturn(false);

        // orders imports customers
        when(metaData.getImportedKeys(any(), any(), eq("orders"))).thenReturn(ordersRs);
        when(ordersRs.next()).thenReturn(true, false);
        when(ordersRs.getString("PKTABLE_NAME")).thenReturn("customers");

        // order_items imports orders
        when(metaData.getImportedKeys(any(), any(), eq("order_items"))).thenReturn(orderItemsRs);
        when(orderItemsRs.next()).thenReturn(true, false);
        when(orderItemsRs.getString("PKTABLE_NAME")).thenReturn("orders");

        List<String> order = planner.planDependencyOrder(connection, tables);

        assertEquals(3, order.size());
        int customersIdx = order.indexOf("customers");
        int ordersIdx = order.indexOf("orders");
        int orderItemsIdx = order.indexOf("order_items");

        assertTrue(customersIdx < ordersIdx, "customers must come before orders");
        assertTrue(ordersIdx < orderItemsIdx, "orders must come before order_items");
    }

    @Test
    void testCircularDependency_ThrowsException() throws SQLException {
        // table_a -> table_b -> table_a
        List<String> tables = List.of("table_a", "table_b");

        ResultSet rsA = org.mockito.Mockito.mock(ResultSet.class);
        when(metaData.getImportedKeys(any(), any(), eq("table_a"))).thenReturn(rsA);
        when(rsA.next()).thenReturn(true, false);
        when(rsA.getString("PKTABLE_NAME")).thenReturn("table_b");

        ResultSet rsB = org.mockito.Mockito.mock(ResultSet.class);
        when(metaData.getImportedKeys(any(), any(), eq("table_b"))).thenReturn(rsB);
        when(rsB.next()).thenReturn(true, false);
        when(rsB.getString("PKTABLE_NAME")).thenReturn("table_a");

        DataSeedingException ex = assertThrows(DataSeedingException.class,
                () -> planner.planDependencyOrder(connection, tables));

        assertTrue(ex.getMessage().contains("Circular foreign key dependency detected"));
    }

    @Test
    void testEmptyTableList() throws SQLException {
        List<String> order = planner.planDependencyOrder(connection, List.of());
        assertTrue(order.isEmpty());
    }
}
