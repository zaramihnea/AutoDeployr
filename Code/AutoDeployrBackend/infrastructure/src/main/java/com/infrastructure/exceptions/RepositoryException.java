package com.infrastructure.exceptions;

/**
 * Exception for repository operations failures
 */
public class RepositoryException extends InfrastructureException {
    private final String entityType;

    public RepositoryException(String entityType, String message) {
        super(message, "REPOSITORY_ERROR");
        this.entityType = entityType;
    }

    public RepositoryException(String entityType, String message, Throwable cause) {
        super(message, "REPOSITORY_ERROR", cause);
        this.entityType = entityType;
    }

    public String getEntityType() {
        return entityType;
    }
} 