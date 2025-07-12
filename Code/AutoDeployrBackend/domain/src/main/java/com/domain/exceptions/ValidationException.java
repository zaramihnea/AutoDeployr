package com.domain.exceptions;

public class ValidationException extends DomainException {
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR", 400);
    }

    public ValidationException(String field, String message) {
        super(field + ": " + message, "VALIDATION_ERROR", 400);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, "VALIDATION_ERROR", 400, cause);
    }
}
