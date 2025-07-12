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
 * Request DTO for deploying an application
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeployApplicationRequest {
    private String appPath;

    /**
     * Environment variables to be passed to the deployed functions
     */
    @Builder.Default
    private Map<String, String> environmentVariables = new HashMap<>();

    /**
     * Whether to deploy functions as private (requires API key) or public
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
        if (appPath == null || appPath.trim().isEmpty()) {
            throw new ValidationException("appPath", "Application path cannot be empty");
        }
        if (environmentVariables == null) {
            environmentVariables = new HashMap<>();
        }
    }
}