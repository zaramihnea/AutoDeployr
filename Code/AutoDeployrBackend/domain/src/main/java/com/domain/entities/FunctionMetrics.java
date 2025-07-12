package com.domain.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FunctionMetrics {
    private String id;
    private String functionId;
    private String functionName;
    private String appName;
    private String userId;

    @Builder.Default
    private long invocationCount = 0;

    @Builder.Default
    private long successCount = 0;

    @Builder.Default
    private long failureCount = 0;

    @Builder.Default
    private long totalExecutionTimeMs = 0;

    @Builder.Default
    private long minExecutionTimeMs = Long.MAX_VALUE;

    @Builder.Default
    private long maxExecutionTimeMs = 0;

    @Builder.Default
    private LocalDateTime lastInvoked = null;

    /**
     * Record a function execution
     *
     * @param executionTimeMs Execution time in milliseconds
     * @param successful Whether the execution was successful
     */
    public void recordExecution(long executionTimeMs, boolean successful) {
        this.invocationCount++;
        if (successful) {
            this.successCount++;
        } else {
            this.failureCount++;
        }

        this.totalExecutionTimeMs += executionTimeMs;
        this.minExecutionTimeMs = Math.min(this.minExecutionTimeMs, executionTimeMs);
        this.maxExecutionTimeMs = Math.max(this.maxExecutionTimeMs, executionTimeMs);
        this.lastInvoked = LocalDateTime.now();
    }

    /**
     * Get average execution time in milliseconds
     *
     * @return Average execution time or 0 if no executions
     */
    public long getAverageExecutionTimeMs() {
        return invocationCount > 0 ? totalExecutionTimeMs / invocationCount : 0;
    }

    /**
     * Get success rate percentage
     *
     * @return Success rate or 0 if no executions
     */
    public double getSuccessRate() {
        return invocationCount > 0 ? (double) successCount / invocationCount * 100 : 0;
    }
}