package com.application.exceptions;

import com.domain.exceptions.BaseException;

public class ApplicationException extends BaseException {
    public ApplicationException(String message, String errorCode, Integer statusCode) {
        super(message, errorCode, statusCode);
    }

    public ApplicationException(String message, String errorCode, Integer statusCode, Throwable cause) {
        super(message, errorCode, statusCode, cause);
    }

    public ApplicationException(String message, String errorCode) {
        super(message, errorCode, 500); // Default to 500 Internal Server Error
    }

    public ApplicationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, 500, cause); // Default to 500 Internal Server Error
    }
}
