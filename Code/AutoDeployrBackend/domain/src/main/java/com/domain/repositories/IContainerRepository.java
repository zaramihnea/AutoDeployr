package com.domain.repositories;

import com.domain.entities.Container;
import com.domain.entities.FunctionExecutionResult;

import java.util.Map;

/**
 * Repository interface for container operations
 */
public interface IContainerRepository {
    /**
     * Create a container for a function
     *
     * @param functionName Name of the function
     * @param buildPath Path to the function build directory
     * @return Created container
     */
    Container createContainer(String functionName, String buildPath, Map<String, String> enviornmentVariables);

    /**
     * Execute a function in a container
     *
     * @param container Container to execute the function in
     * @param event Event data to pass to the function
     * @return Function execution result
     */
    FunctionExecutionResult executeFunction(Container container, Map<String, Object> event);

    /**
     * Clean up resources for a function
     *
     * @param functionName Name of the function
     * @return Success status
     */
    boolean cleanupImage(String functionName);

    /**
     * Clean up resources for a function belonging to a specific user
     *
     * @param functionName Name of the function
     * @param userId User ID who owns the function
     * @return Success status
     */
    boolean cleanupImageForUser(String functionName, String userId);

    /**
     * Sanitize a string to be valid for Docker image tags
     * Docker tags must be lowercase and can only contain letters, digits, underscores, periods and dashes
     * Must not start or end with a separator
     *
     * @param input The input string to sanitize
     * @return A sanitized string valid for Docker tags
     */
    String sanitizeForDockerTag(String input);
}