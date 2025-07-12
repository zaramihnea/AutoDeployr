package com.infrastructure.exceptions;

import com.domain.exceptions.BaseException;

public class InfrastructureException extends BaseException {
    public InfrastructureException(String message, String errorCode, Integer statusCode) {
        super(message, errorCode, statusCode);
    }

    public InfrastructureException(String message, String errorCode, Integer statusCode, Throwable cause) {
        super(message, errorCode, statusCode, cause);
    }

    public InfrastructureException(String message, String errorCode) {
        super(message, errorCode, 500); // Default to 500 Internal Server Error
    }

    public InfrastructureException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, 500, cause);
    }
}
