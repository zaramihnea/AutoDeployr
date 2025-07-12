package com.application.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for summarized function information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionSummaryResponse {
    private String id;
    private String name;
    private String path;
    private List<String> methods;
    private String appName;
    private String projectId;
    private String userId;

    /**
     * Deployment status: "deployed", "pending", "failed", etc.
     */
    private String status;

    /**
     * When the function was last invoked
     */
    private LocalDateTime lastInvoked;

    /**
     * Summary metrics
     */
    private long invocationCount;
    private double averageExecutionTimeMs;
    private double successRate;

    /**
     * Function language and framework
     */
    private String language;
    private String framework;

    /**
     * Security information
     */
    private boolean isPrivate;
    private String apiKey;
    private LocalDateTime apiKeyGeneratedAt;
}