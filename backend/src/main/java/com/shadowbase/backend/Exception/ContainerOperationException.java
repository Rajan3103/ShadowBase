package com.shadowbase.backend.Exception;

public class ContainerOperationException extends RuntimeException {
    public ContainerOperationException(String message) {
        super(message);
    }

    public ContainerOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
