package com.domain.repositories;

import com.domain.entities.FunctionMetrics;
import java.util.List;
import java.util.Optional;

public interface IFunctionMetricsRepository {
    /**
     * Find metrics by function ID
     *
     * @param functionId Function ID
     * @return Optional metrics
     */
    Optional<FunctionMetrics> findByFunctionId(String functionId);

    /**
     * Find all metrics for a user's functions
     *
     * @param userId User ID
     * @return List of metrics
     */
    List<FunctionMetrics> findByUserId(String userId);

    /**
     * Find all metrics for an application
     *
     * @param appName Application name
     * @param userId User ID
     * @return List of metrics
     */
    List<FunctionMetrics> findByAppNameAndUserId(String appName, String userId);

    /**
     * Save metrics
     *
     * @param metrics Metrics to save
     * @return Saved metrics
     */
    FunctionMetrics save(FunctionMetrics metrics);
}