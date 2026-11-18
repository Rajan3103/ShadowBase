package com.shadowbase.backend.heap.parser;

/**
 * Exception thrown when parsing of an HPROF binary heap dump fails
 * due to corruption, truncation, or format incompatibility.
 */
public class HprofParseException extends RuntimeException {

    public HprofParseException(String message) {
        super(message);
    }

    public HprofParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
