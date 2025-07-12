package com.domain.exceptions;

import lombok.Getter;

/**
 * Base exception for all domain-related exceptions
 * Serves as the foundation for the exception hierarchy
 */
@Getter
public class BaseException extends RuntimeException {
    private final String errorCode;
    private final Integer statusCode;

    /**
     * Create a new base exception
     *
     * @param message Error message
     * @param errorCode Error code for programmatic identification
     * @param statusCode HTTP status code (useful for web APIs)
     */
    public BaseException(String message, String errorCode, Integer statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    /**
     * Create a new base exception with cause
     *
     * @param message Error message
     * @param errorCode Error code for programmatic identification
     * @param statusCode HTTP status code (useful for web APIs)
     * @param cause The original exception
     */
    public BaseException(String message, String errorCode, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    /**
     * Create a base exception with default status code 500
     *
     * @param message Error message
     * @param errorCode Error code
     */
    public BaseException(String message, String errorCode) {
        this(message, errorCode, 500);
    }

    /**
     * Create a base exception with default status code 500 and cause
     *
     * @param message Error message
     * @param errorCode Error code
     * @param cause The original exception
     */
    public BaseException(String message, String errorCode, Throwable cause) {
        this(message, errorCode, 500, cause);
    }
}