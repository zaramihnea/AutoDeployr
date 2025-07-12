package com.application.dtos.response.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionMetricsResponse {
    private String functionId;
    private String functionName;
    private String appName;

    private long invocationCount;
    private long successCount;
    private long failureCount;
    private double successRate;

    private long totalExecutionTimeMs;
    private long averageExecutionTimeMs;
    private long minExecutionTimeMs;
    private long maxExecutionTimeMs;

    private LocalDateTime lastInvoked;
}