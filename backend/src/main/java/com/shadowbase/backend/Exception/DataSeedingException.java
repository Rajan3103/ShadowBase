package com.shadowbase.backend.Exception;

/**
 * Custom runtime exception thrown when data seeding validation, planning,
 * connection establishment, or streaming transfer encounters an error.
 */
public class DataSeedingException extends RuntimeException {

    public DataSeedingException(String message) {
        super(message);
    }

    public DataSeedingException(String message, Throwable cause) {
        super(message, cause);
    }
}
