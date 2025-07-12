package com.domain.exceptions;

public class DomainException extends BaseException {
    public DomainException(String message, String errorCode, Integer statusCode) {
        super(message, errorCode, statusCode);
    }

    public DomainException(String message, String errorCode, Integer statusCode, Throwable cause) {
        super(message, errorCode, statusCode, cause);
    }

    public DomainException(String message, String errorCode) {
        super(message, errorCode, 400); // Default to 400 Bad Request for domain errors
    }

    public DomainException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, 400, cause);
    }
}
