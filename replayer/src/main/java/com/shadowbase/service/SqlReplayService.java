package com.shadowbase.service;

import com.shadowbase.model.ShadowBaseMessage;

public class SqlReplayService {

    public void replay(ShadowBaseMessage message) {

        String sql = buildSql(message);

        System.out.println("\n----- SQL Replay -----");
        System.out.println("Executing SQL:");
        System.out.println(sql);
        System.out.println("----------------------");
    }

    private String buildSql(ShadowBaseMessage message) {

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

        return "INSERT INTO " +
                message.getTable() +
                " VALUES (" +
                message.getData() +
                ");";
    }

    private String buildUpdateSql(ShadowBaseMessage message) {

        return "UPDATE " +
                message.getTable() +
                " SET " +
                message.getData() +
                " WHERE id = " +
                message.getId() +
                ";";
    }

    private String buildDeleteSql(ShadowBaseMessage message) {

        return "DELETE FROM " +
                message.getTable() +
                " WHERE id = " +
                message.getId() +
                ";";
    }
}