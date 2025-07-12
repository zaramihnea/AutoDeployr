package com.domain.exceptions;

public class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String resourceType, String identifier) {
        super(resourceType + " not found with identifier: " + identifier,
                "RESOURCE_NOT_FOUND", 404);
    }

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", 404);
    }
}
