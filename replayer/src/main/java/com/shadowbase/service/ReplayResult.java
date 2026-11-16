package com.shadowbase.service;

public class ReplayResult {

    private final boolean success;
    private final String sql;
    private final String errorMessage;

    private ReplayResult(boolean success, String sql, String errorMessage) {
        this.success = success;
        this.sql = sql;
        this.errorMessage = errorMessage;
    }

    public static ReplayResult success(String sql) {
        return new ReplayResult(true, sql, null);
    }

    public static ReplayResult failure(String sql, String errorMessage) {
        return new ReplayResult(false, sql, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSql() {
        return sql;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}