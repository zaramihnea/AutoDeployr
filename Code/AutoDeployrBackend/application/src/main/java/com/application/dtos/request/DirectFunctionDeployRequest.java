package com.application.dtos.request;

import com.domain.exceptions.ValidationException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for deploying a function created directly in the frontend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectFunctionDeployRequest {
    private String appName;
    private String language;
    private String functionCode;

    /**
     * Environment variables to be passed to the deployed function
     */
    @Builder.Default
    private Map<String, String> environmentVariables = new HashMap<>();

    /**
     * Whether to deploy the function as private (requires API key) or public
     */
    @Builder.Default
    @JsonProperty("isPrivate")
    private boolean isPrivate = false;

    /**
     * Validate the request
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {

        if (language == null || language.trim().isEmpty()) {
            throw new ValidationException("language", "Programming language cannot be empty");
        }

        if (functionCode == null || functionCode.trim().isEmpty()) {
            throw new ValidationException("functionCode", "Function code cannot be empty");
        }

        if (environmentVariables == null) {
            environmentVariables = new HashMap<>();
        }
    }
}