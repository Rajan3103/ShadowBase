package com.shadowbase.service;

import com.shadowbase.model.ShadowBaseMessage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SqlReplayService {

    private final ReplayMetrics metrics;

    private final String databaseUrl;
    private final String databaseUser;
    private final String databasePassword;

    public SqlReplayService() {

        this.metrics = new ReplayMetrics();

        this.databaseUrl = getEnvironmentVariable(
                "SHADOWBASE_DB_URL",
                "jdbc:postgresql://localhost:5432/shadowbase"
        );

        this.databaseUser = getEnvironmentVariable(
                "SHADOWBASE_DB_USER",
                "postgres"
        );

        this.databasePassword = getEnvironmentVariable(
                "SHADOWBASE_DB_PASSWORD",
                "postgres"
        );
    }

    public ReplayResult replay(ShadowBaseMessage message) {

        String sql = buildSql(message);

        System.out.println();
        System.out.println("----- SQL Replay -----");
        System.out.println("ID        : " + message.getId());
        System.out.println("Operation : " + message.getOperation());
        System.out.println("Table     : " + message.getTable());
        System.out.println("Executing : " + sql);

        try (
                Connection connection = DriverManager.getConnection(
                        databaseUrl,
                        databaseUser,
                        databasePassword
                );

                Statement statement = connection.createStatement()
        ) {

            statement.executeUpdate(sql);

            metrics.recordSuccess();

            System.out.println("Replay status : SUCCESS");

            ReplayResult result = ReplayResult.success(sql);

            printMetrics();

            return result;

        } catch (SQLException exception) {

            metrics.recordFailure();

            System.out.println("Replay status : FAILED");
            System.out.println("Error         : " + exception.getMessage());

            ReplayResult result = ReplayResult.failure(
                    sql,
                    exception.getMessage()
            );

            printMetrics();

            return result;
        }
    }

    private String buildSql(ShadowBaseMessage message) {

        if (message.getOperation() == null) {
            throw new IllegalArgumentException("Operation cannot be null");
        }

        String operation = message.getOperation().toUpperCase();

        return switch (operation) {

            case "INSERT" ->
                    buildInsertSql(message);

            case "UPDATE" ->
                    buildUpdateSql(message);

            case "DELETE" ->
                    buildDeleteSql(message);

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported operation: " + operation
                    );
        };
    }

    private String buildInsertSql(ShadowBaseMessage message) {

        return "INSERT INTO "
                + message.getTable()
                + " VALUES ("
                + message.getData()
                + ");";
    }

    private String buildUpdateSql(ShadowBaseMessage message) {

        return "UPDATE "
                + message.getTable()
                + " SET "
                + message.getData()
                + " WHERE id = "
                + message.getId()
                + ";";
    }

    private String buildDeleteSql(ShadowBaseMessage message) {

        return "DELETE FROM "
                + message.getTable()
                + " WHERE id = "
                + message.getId()
                + ";";
    }

    private String getEnvironmentVariable(
            String variableName,
            String defaultValue
    ) {

        String value = System.getenv(variableName);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }

    public void printMetrics() {
        metrics.printMetrics();
    }

    public ReplayMetrics getMetrics() {
        return metrics;
    }
}