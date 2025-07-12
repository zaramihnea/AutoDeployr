package com.webapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for resource not found errors (HTTP 404)
 */
public class ResourceNotFoundException extends APIException {

    /**
     * Create a new resource not found exception
     *
     * @param resourceType Type of resource (e.g., "Function", "Application")
     * @param identifier Resource identifier (e.g., function name)
     */
    public ResourceNotFoundException(String resourceType, String identifier) {
        super(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                String.format("%s not found with identifier: %s", resourceType, identifier)
        );
    }

    /**
     * Create a new resource not found exception with a custom message
     *
     * @param message Custom error message
     */
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }
}