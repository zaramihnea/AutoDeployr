package com.infrastructure.exceptions;

/**
 * Exception for Git operation errors
 */
public class GitOperationException extends RuntimeException {

    /**
     * Create a new GitOperationException
     *
     * @param message Error message
     */
    public GitOperationException(String message) {
        super(message);
    }

    /**
     * Create a new GitOperationException with a cause
     *
     * @param message Error message
     * @param cause Cause of the exception
     */
    public GitOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}