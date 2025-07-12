package com.application.usecases.commands;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Command to deploy an application
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeployApplicationCommand {
    private String appPath;
    private String userId;
    private String appName;

    /**
     * Environment variables to be passed to the deployed functions
     */
    @Builder.Default
    private Map<String, String> environmentVariables = new HashMap<>();

    /**
     * Whether to deploy functions as private (requires API key) or public
     */
    @Builder.Default
    private boolean isPrivate = false;

    /**
     * Validate the command
     *
     * @throws ValidationException If the command is invalid
     */
    public void validate() {
        if (appPath == null || appPath.trim().isEmpty()) {
            throw new ValidationException("appPath", "Application path cannot be empty");
        }

        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("userId", "User ID cannot be empty");
        }

        if (environmentVariables == null) {
            environmentVariables = new HashMap<>();
        }
    }
}