package com.domain.services;

import com.domain.entities.FunctionMetrics;

public interface IMetricsService {
    /**
     * Record a function execution
     *
     * @param functionId Function ID
     * @param executionTimeMs Execution time in milliseconds
     * @param successful Whether the execution was successful
     * @return Updated metrics
     */
    FunctionMetrics recordExecution(String functionId, long executionTimeMs, boolean successful);

    /**
     * Get metrics for a function
     *
     * @param functionId Function ID
     * @return Metrics for the function
     */
    FunctionMetrics getFunctionMetrics(String functionId);

}