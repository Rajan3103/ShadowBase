package com.shadowbase.backend.Exception;

/**
 * Exception thrown when validation, connection, or execution fails during
 * an SQL schema import workflow into a sandbox container.
 */
public class SchemaImportException extends RuntimeException {

    public SchemaImportException(String message) {
        super(message);
    }

    public SchemaImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
