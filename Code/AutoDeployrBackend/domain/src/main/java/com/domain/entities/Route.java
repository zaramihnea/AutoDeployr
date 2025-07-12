package com.domain.entities;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a route in an application
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Route {
    private String name;
    private String path;
    private List<String> methods;
    private String source;
    private String appName;
    private String filePath;
    private String functionName;
    private String handlerMethod;
    private String classPath;

    /**
     * Validate the route
     *
     * @throws ValidationException If the route is invalid
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("name", "Route name cannot be empty");
        }

        if (path == null || path.trim().isEmpty()) {
            throw new ValidationException("path", "Route path cannot be empty");
        }

        if (methods == null || methods.isEmpty()) {
            throw new ValidationException("methods", "Route must have at least one HTTP method");
        }

        if (source == null || source.trim().isEmpty()) {
            throw new ValidationException("source", "Route source code cannot be empty");
        }
        for (String method : methods) {
            if (!isValidHttpMethod(method)) {
                throw new ValidationException("methods", "Invalid HTTP method: " + method);
            }
        }

        if (appName == null || appName.trim().isEmpty()) {
            throw new ValidationException("appName", "Application name cannot be empty");
        }
    }

    /**
     * Check if an HTTP method is valid
     *
     * @param method HTTP method to check
     * @return true if valid, false otherwise
     */
    private boolean isValidHttpMethod(String method) {
        if (method == null) return false;

        String normalizedMethod = method.toUpperCase();
        return normalizedMethod.equals("GET") ||
                normalizedMethod.equals("POST") ||
                normalizedMethod.equals("PUT") ||
                normalizedMethod.equals("DELETE") ||
                normalizedMethod.equals("PATCH") ||
                normalizedMethod.equals("OPTIONS") ||
                normalizedMethod.equals("HEAD");
    }

}