package com.infrastructure.persistence.repository;

import com.infrastructure.persistence.entity.FunctionMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringFunctionMetricsRepository extends JpaRepository<FunctionMetricsEntity, String> {
    /**
     * Find metrics by function ID
     */
    Optional<FunctionMetricsEntity> findByFunctionId(String functionId);

    /**
     * Find all metrics for a user's functions
     */
    List<FunctionMetricsEntity> findByUserId(String userId);

    /**
     * Find all metrics for an application
     */
    List<FunctionMetricsEntity> findByAppNameAndUserId(String appName, String userId);
}