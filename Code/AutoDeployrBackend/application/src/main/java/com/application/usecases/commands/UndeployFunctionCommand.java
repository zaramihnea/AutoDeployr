package com.application.usecases.commands;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command to undeploy a function
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UndeployFunctionCommand {
    private String appName;
    private String functionName;
    private String user;
    private String userId;

    /**
     * Validate the command
     *
     * @throws ValidationException If the command is invalid
     */
    public void validate() {
        if (appName != null && appName.trim().isEmpty()) {
            throw new ValidationException("appName", "Application name cannot be empty");
        }
        if (functionName == null || functionName.trim().isEmpty()) {
            throw new ValidationException("functionName", "Function name cannot be empty");
        }
        if (user == null || user.trim().isEmpty()) {
            throw new ValidationException("user", "User ID cannot be empty");
        }
        if (userId == null || userId.trim().isEmpty()) {
            userId = user;
        }
    }
    
    /**
     * Get the user ID (either from userId field or user field for backward compatibility)
     * 
     * @return The user ID
     */
    public String getUserId() {
        return userId != null && !userId.trim().isEmpty() ? userId : user;
    }
}