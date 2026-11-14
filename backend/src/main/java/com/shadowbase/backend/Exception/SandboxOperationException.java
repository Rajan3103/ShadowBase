package com.shadowbase.backend.Exception;

public class SandboxOperationException extends RuntimeException {
    public SandboxOperationException(String message) {
        super(message);
    }

    public SandboxOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
