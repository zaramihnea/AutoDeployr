package com.webapi.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception class for API errors
 */
@Getter
public class APIException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    /**
     * Create a new API exception with the specified status, error code, and message
     *
     * @param status HTTP status code
     * @param errorCode Error code for programmatic identification
     * @param message Error message
     */
    public APIException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Create a new API exception with the specified status, error code, message, and cause
     *
     * @param status HTTP status code
     * @param errorCode Error code for programmatic identification
     * @param message Error message
     * @param cause Original exception
     */
    public APIException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    /**
     * Create a new API exception with the specified status and message
     *
     * @param status HTTP status code
     * @param message Error message
     */
    public APIException(HttpStatus status, String message) {
        this(status, "API_ERROR", message);
    }
}