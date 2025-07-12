package com.infrastructure.exceptions;

/**
 * Exception for database and persistence-related errors.
 * More specific than StorageException, focused on database operations.
 */
public class PersistenceException extends InfrastructureException {
    public PersistenceException(String message) {
        super(message, "PERSISTENCE_ERROR");
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, "PERSISTENCE_ERROR", cause);
    }

    public PersistenceException(String operation, String message) {
        super("Database operation '" + operation + "' failed: " + message, "PERSISTENCE_ERROR");
    }

    public PersistenceException(String operation, String message, Throwable cause) {
        super("Database operation '" + operation + "' failed: " + message, "PERSISTENCE_ERROR", cause);
    }
}