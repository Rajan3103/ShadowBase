package com.shadowbase.backend.Exception;

/**
 * Exception thrown when execution of an ad-hoc SQL query or script fails
 * against a sandbox database container.
 */
public class SqlQueryException extends RuntimeException {

    public SqlQueryException(String message) {
        super(message);
    }

    public SqlQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
