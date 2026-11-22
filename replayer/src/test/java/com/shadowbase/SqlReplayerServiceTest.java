package com.shadowbase;

import com.shadowbase.model.ShadowBaseMessage;
import com.shadowbase.service.ReplayResult;
import com.shadowbase.service.SqlReplayService;
import org.junit.jupiter.api.*;

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqlReplayServiceIntegrationTest {

    private static SqlReplayService replayService;

    private static final String DB_URL =
            "jdbc:postgresql://localhost:5432/shadowbase";

    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "postgres";

    @BeforeAll
    static void setup() throws SQLException {
        replayService = new SqlReplayService();

        try (Connection connection =
                     DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS replayer_test_customers (
                        id INT PRIMARY KEY,
                        name VARCHAR(100),
                        email VARCHAR(150)
                    )
                    """);

            statement.executeUpdate(
                    "DELETE FROM replayer_test_customers"
            );
        }
    }

    @Test
    @Order(1)
    void shouldReplayInsert() throws SQLException {

        ShadowBaseMessage message = new ShadowBaseMessage(
                101,
                "INSERT",
                "replayer_test_customers",
                "101, 'Rohan', 'rohan@test.com'"
        );

        ReplayResult result = replayService.replay(message);

        assertTrue(result.isSuccess());
        assertEquals(
                "INSERT INTO replayer_test_customers VALUES " +
                        "(101, 'Rohan', 'rohan@test.com');",
                result.getSql()
        );

        try (Connection connection =
                     DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT name, email FROM replayer_test_customers WHERE id = ?")) {

            statement.setInt(1, 101);

            try (ResultSet resultSet = statement.executeQuery()) {

                assertTrue(resultSet.next());

                assertEquals(
                        "Rohan",
                        resultSet.getString("name")
                );

                assertEquals(
                        "rohan@test.com",
                        resultSet.getString("email")
                );
            }
        }
    }

    @Test
    @Order(2)
    void shouldReplayUpdate() throws SQLException {

        ShadowBaseMessage message = new ShadowBaseMessage(
                101,
                "UPDATE",
                "replayer_test_customers",
                "name = 'Updated Rohan', email = 'updated@test.com'"
        );

        ReplayResult result = replayService.replay(message);

        assertTrue(result.isSuccess());

        try (Connection connection =
                     DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT name, email FROM replayer_test_customers WHERE id = ?")) {

            statement.setInt(1, 101);

            try (ResultSet resultSet = statement.executeQuery()) {

                assertTrue(resultSet.next());

                assertEquals(
                        "Updated Rohan",
                        resultSet.getString("name")
                );

                assertEquals(
                        "updated@test.com",
                        resultSet.getString("email")
                );
            }
        }
    }

    @Test
    @Order(3)
    void shouldReplayDelete() throws SQLException {

        ShadowBaseMessage message = new ShadowBaseMessage(
                101,
                "DELETE",
                "replayer_test_customers",
                ""
        );

        ReplayResult result = replayService.replay(message);

        assertTrue(result.isSuccess());

        try (Connection connection =
                     DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM replayer_test_customers WHERE id = ?")) {

            statement.setInt(1, 101);

            try (ResultSet resultSet = statement.executeQuery()) {

                assertTrue(resultSet.next());

                assertEquals(
                        0,
                        resultSet.getInt(1)
                );
            }
        }
    }

    @Test
    @Order(4)
    void shouldFailForInvalidOperation() {

        ShadowBaseMessage message = new ShadowBaseMessage(
                102,
                "DROP",
                "replayer_test_customers",
                ""
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> replayService.replay(message)
        );
    }

    @Test
    @Order(5)
    void shouldUpdateReplayMetrics() {

        long before =
                replayService.getMetrics().getTotalReplays();

        ShadowBaseMessage message = new ShadowBaseMessage(
                103,
                "INSERT",
                "replayer_test_customers",
                "103, 'Test', 'test@test.com'"
        );

        ReplayResult result = replayService.replay(message);

        assertTrue(result.isSuccess());

        assertEquals(
                before + 1,
                replayService.getMetrics().getTotalReplays()
        );

        assertTrue(
                replayService.getMetrics().getSuccessfulReplays() >= 1
        );
    }

    @AfterAll
    static void cleanup() throws SQLException {

        try (Connection connection =
                     DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(
                    "DROP TABLE IF EXISTS replayer_test_customers"
            );
        }
    }
}