package com.infrastructure.exceptions;

public class StorageException extends InfrastructureException {
    public StorageException(String message) {
        super(message, "STORAGE_ERROR");
    }

    public StorageException(String message, Throwable cause) {
        super(message, "STORAGE_ERROR", cause);
    }
}
