package com.application.usecases.commands;

import com.application.dtos.response.FunctionResponse;
import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvokeFunctionCommand {
    private String appName;
    private String functionName;
    private String httpMethod;
    private String userId; // The authenticated user making the request
    private String targetUsername; // The username from the URL path (whose function to invoke)
    private String body;

    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    @Builder.Default
    private Map<String, String> queryParams = new HashMap<>();

    @Builder.Default
    private Map<String, Object> event = new HashMap<>();

    /**
     * Validate the command
     *
     * @throws ValidationException If the command is invalid
     */
    public void validate() {
        if (functionName == null || functionName.trim().isEmpty()) {
            throw new ValidationException("functionName", "Function name cannot be empty");
        }

        if (appName == null || appName.trim().isEmpty()) {
            throw new ValidationException("appName", "Application name cannot be empty");
        }

        if (httpMethod == null || httpMethod.trim().isEmpty()) {
            throw new ValidationException("httpMethod", "HTTP method cannot be empty");
        }

        if (userId == null || userId.trim().isEmpty()) {
            userId = "anonymous";
        }

        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new ValidationException("targetUsername", "Target username cannot be empty");
        }

        if (headers == null) {
            headers = new HashMap<>();
        }

        if (queryParams == null) {
            queryParams = new HashMap<>();
        }

        if (event == null) {
            event = new HashMap<>();
        }
    }
}