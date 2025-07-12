package com.application.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for deployment operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentResponse {
    /**
     * Status of the deployment: "success", "error", or "partial"
     */
    private String status;

    /**
     * List of successfully deployed function names
     */
    private List<String> deployedFunctions;

    /**
     * List of function names that failed to deploy
     */
    private List<String> failedFunctions;

    /**
     * Error message if deployment failed
     */
    private String error;

    /**
     * General message about the deployment
     */
    private String message;

    /**
     * Detailed information about deployed functions including URLs and API keys
     */
    private List<DeployedFunctionInfo> deployedFunctionDetails;

    /**
     * Information about a deployed function
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeployedFunctionInfo {
        private String functionName;
        private String appName;
        private String functionUrl;
        private boolean isPrivate;
        private String apiKey;
        private List<String> supportedMethods;
    }
}