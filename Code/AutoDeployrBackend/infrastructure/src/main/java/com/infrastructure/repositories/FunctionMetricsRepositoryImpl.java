package com.infrastructure.repositories;

import com.domain.entities.FunctionMetrics;
import com.domain.repositories.IFunctionMetricsRepository;
import com.infrastructure.exceptions.PersistenceException;
import com.infrastructure.persistence.entity.FunctionMetricsEntity;
import com.infrastructure.persistence.repository.SpringFunctionMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class FunctionMetricsRepositoryImpl implements IFunctionMetricsRepository {
    private static final Logger logger = LoggerFactory.getLogger(FunctionMetricsRepositoryImpl.class);

    private final SpringFunctionMetricsRepository springFunctionMetricsRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<FunctionMetrics> findByFunctionId(String functionId) {
        try {
            return springFunctionMetricsRepository.findByFunctionId(functionId)
                    .map(this::mapToMetrics);
        } catch (Exception e) {
            logger.error("Error finding metrics by function ID {}: {}", functionId, e.getMessage(), e);
            throw new PersistenceException("Error finding metrics by function ID", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FunctionMetrics> findByUserId(String userId) {
        try {
            return springFunctionMetricsRepository.findByUserId(userId)
                    .stream()
                    .map(this::mapToMetrics)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error finding metrics by user ID {}: {}", userId, e.getMessage(), e);
            throw new PersistenceException("Error finding metrics by user ID", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<FunctionMetrics> findByAppNameAndUserId(String appName, String userId) {
        try {
            return springFunctionMetricsRepository.findByAppNameAndUserId(appName, userId)
                    .stream()
                    .map(this::mapToMetrics)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error finding metrics by app name {} and user ID {}: {}",
                    appName, userId, e.getMessage(), e);
            throw new PersistenceException("Error finding metrics by app name and user ID", e);
        }
    }

    @Override
    @Transactional
    public FunctionMetrics save(FunctionMetrics metrics) {
        try {
            if (metrics.getId() == null) {
                metrics.setId(UUID.randomUUID().toString());
            }

            FunctionMetricsEntity entity = mapToEntity(metrics);
            FunctionMetricsEntity savedEntity = springFunctionMetricsRepository.save(entity);
            return mapToMetrics(savedEntity);
        } catch (Exception e) {
            logger.error("Error saving metrics for function {}: {}",
                    metrics.getFunctionId(), e.getMessage(), e);
            throw new PersistenceException("Error saving function metrics", e);
        }
    }

    /**
     * Map from Entity to Domain model
     */
    private FunctionMetrics mapToMetrics(FunctionMetricsEntity entity) {
        return FunctionMetrics.builder()
                .id(entity.getId())
                .functionId(entity.getFunctionId())
                .functionName(entity.getFunctionName())
                .appName(entity.getAppName())
                .userId(entity.getUserId())
                .invocationCount(entity.getInvocationCount())
                .successCount(entity.getSuccessCount())
                .failureCount(entity.getFailureCount())
                .totalExecutionTimeMs(entity.getTotalExecutionTimeMs())
                .minExecutionTimeMs(entity.getMinExecutionTimeMs())
                .maxExecutionTimeMs(entity.getMaxExecutionTimeMs())
                .lastInvoked(entity.getLastInvoked())
                .build();
    }

    /**
     * Map from Domain model to Entity
     */
    private FunctionMetricsEntity mapToEntity(FunctionMetrics metrics) {
        return FunctionMetricsEntity.builder()
                .id(metrics.getId())
                .functionId(metrics.getFunctionId())
                .functionName(metrics.getFunctionName())
                .appName(metrics.getAppName())
                .userId(metrics.getUserId())
                .invocationCount(metrics.getInvocationCount())
                .successCount(metrics.getSuccessCount())
                .failureCount(metrics.getFailureCount())
                .totalExecutionTimeMs(metrics.getTotalExecutionTimeMs())
                .minExecutionTimeMs(metrics.getMinExecutionTimeMs())
                .maxExecutionTimeMs(metrics.getMaxExecutionTimeMs())
                .lastInvoked(metrics.getLastInvoked())
                .build();
    }
}