package com.webapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for authentication-related errors
 */
public class AuthenticationException extends APIException {

    /**
     * Create a new authentication exception
     *
     * @param message Error message
     */
    public AuthenticationException(String message) {
        super(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_ERROR", message);
    }

    /**
     * Create a new authentication exception with a cause
     *
     * @param message Error message
     * @param cause Original exception
     */
    public AuthenticationException(String message, Throwable cause) {
        super(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_ERROR", message, cause);
    }

    /**
     * Create an invalid credentials exception
     *
     * @return New authentication exception
     */
    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException("Invalid username or password");
    }

    /**
     * Create an invalid token exception
     *
     * @return New authentication exception
     */
    public static AuthenticationException invalidToken() {
        return new AuthenticationException("Invalid or expired token");
    }

    /**
     * Create an access denied exception
     *
     * @return New authentication exception
     */
    public static AuthenticationException accessDenied() {
        return new AuthenticationException("Access denied");
    }
}