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
 * Request DTO for deploying an application from a GitHub repository
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubDeployRequest {
    private String repositoryUrl;
    private String branch;
    private String customAppName;
    private String language; // Optional, for custom language selection

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
     * Optional GitHub credentials for private repositories
     */
    private String username;
    private String token;

    /**
     * Validate the request
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
        if (repositoryUrl == null || repositoryUrl.trim().isEmpty()) {
            throw new ValidationException("repositoryUrl", "Repository URL cannot be empty");
        }

        if (!isValidGitHubUrl(repositoryUrl)) {
            throw new ValidationException("repositoryUrl", "Invalid GitHub repository URL");
        }

        if (branch == null || branch.trim().isEmpty()) {
            branch = "main";
        }

        if (environmentVariables == null) {
            environmentVariables = new HashMap<>();
        }

        if ((username != null && !username.trim().isEmpty() && (token == null || token.trim().isEmpty())) ||
                (token != null && !token.trim().isEmpty() && (username == null || username.trim().isEmpty()))) {
            throw new ValidationException("credentials", "Both username and token must be provided for private repositories");
        }
    }

    /**
     * Validate that the URL is a GitHub repository URL
     */
    private boolean isValidGitHubUrl(String url) {
        return url.matches("^https://github\\.com/[\\w-]+/[\\w.-]+(\\.git)?$") ||
                url.matches("^git@github\\.com:[\\w-]+/[\\w.-]+(\\.git)?$");
    }
}