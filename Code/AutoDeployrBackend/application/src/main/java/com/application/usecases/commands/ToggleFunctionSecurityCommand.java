package com.application.usecases.commands;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Command for toggling function security between public and private
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToggleFunctionSecurityCommand {
    private String functionId;
    private String userId; // The authenticated user making the request
    private boolean makePrivate; // true to make private, false to make public

    /**
     * Validate the command
     *
     * @throws ValidationException If the command is invalid
     */
    public void validate() {
        if (functionId == null || functionId.trim().isEmpty()) {
            throw new ValidationException("functionId", "Function ID is required");
        }
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("userId", "User ID is required");
        }
    }
} 