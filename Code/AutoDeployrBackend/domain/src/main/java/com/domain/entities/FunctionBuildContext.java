package com.domain.entities;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * Context for building a function
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FunctionBuildContext {
    private Function function;
    private Path appPath;
    private Path buildPath;
    private String language;
    private String framework;

    /**
     * Validate the build context
     *
     * @throws ValidationException If the build context is invalid
     */
    public void validate() {
        if (function == null) {
            throw new ValidationException("function", "Function cannot be null");
        }
        function.validate();

        if (appPath == null) {
            throw new ValidationException("appPath", "Application path cannot be null");
        }

        if (buildPath == null) {
            throw new ValidationException("buildPath", "Build path cannot be null");
        }

        if (language == null || language.trim().isEmpty()) {
            throw new ValidationException("language", "Programming language cannot be empty");
        }

        if (framework == null || framework.trim().isEmpty()) {
            throw new ValidationException("framework", "Framework cannot be empty");
        }
    }

    /**
     * Get the function name
     *
     * @return Function name
     */
    public String getFunctionName() {
        return function != null ? function.getName() : null;
    }

}