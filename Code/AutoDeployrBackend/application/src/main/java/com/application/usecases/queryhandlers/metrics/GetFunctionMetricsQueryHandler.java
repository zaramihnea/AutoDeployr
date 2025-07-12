package com.application.usecases.queryhandlers.metrics;

import com.application.dtos.response.metrics.FunctionMetricsResponse;
import com.application.exceptions.QueryException;
import com.application.usecases.queries.metrics.GetFunctionMetricsQuery;
import com.domain.entities.FunctionMetrics;
import com.domain.services.IMetricsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetFunctionMetricsQueryHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetFunctionMetricsQueryHandler.class);

    private final IMetricsService metricsService;

    /**
     * Handle the get function metrics query
     *
     * @param query Query to handle
     * @return Function metrics response
     * @throws QueryException If retrieval fails
     */
    public FunctionMetricsResponse handle(GetFunctionMetricsQuery query) {
        try {
            logger.info("Getting metrics for function ID: {}", query.getFunctionId());

            FunctionMetrics metrics = metricsService.getFunctionMetrics(query.getFunctionId());

            return mapToResponse(metrics);

        } catch (Exception e) {
            logger.error("Error getting function metrics: {}", e.getMessage(), e);
            throw new QueryException("GetFunctionMetrics", "Error retrieving function metrics: " + e.getMessage(), e);
        }
    }

    private FunctionMetricsResponse mapToResponse(FunctionMetrics metrics) {
        return FunctionMetricsResponse.builder()
                .functionId(metrics.getFunctionId())
                .functionName(metrics.getFunctionName())
                .appName(metrics.getAppName())
                .invocationCount(metrics.getInvocationCount())
                .successCount(metrics.getSuccessCount())
                .failureCount(metrics.getFailureCount())
                .successRate(metrics.getSuccessRate())
                .totalExecutionTimeMs(metrics.getTotalExecutionTimeMs())
                .averageExecutionTimeMs(metrics.getAverageExecutionTimeMs())
                .minExecutionTimeMs(metrics.getMinExecutionTimeMs())
                .maxExecutionTimeMs(metrics.getMaxExecutionTimeMs())
                .lastInvoked(metrics.getLastInvoked())
                .build();
    }
}