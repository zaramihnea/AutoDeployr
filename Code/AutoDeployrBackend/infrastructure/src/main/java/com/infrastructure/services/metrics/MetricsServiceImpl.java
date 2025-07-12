package com.infrastructure.services.metrics;

import com.domain.entities.Function;
import com.domain.entities.FunctionMetrics;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.repositories.IFunctionMetricsRepository;
import com.domain.repositories.IFunctionRepository;
import com.domain.services.IMetricsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MetricsServiceImpl implements IMetricsService {
    private static final Logger logger = LoggerFactory.getLogger(MetricsServiceImpl.class);

    private final IFunctionRepository functionRepository;
    private final IFunctionMetricsRepository metricsRepository;

    @Override
    @Transactional
    public FunctionMetrics recordExecution(String functionId, long executionTimeMs, boolean successful) {
        Function function = functionRepository.findById(functionId)
                .orElseThrow(() -> new ResourceNotFoundException("Function", functionId));
        function.recordExecution(executionTimeMs, successful);
        functionRepository.save(function);
        FunctionMetrics metrics = metricsRepository.findByFunctionId(functionId)
                .orElseGet(() -> {
                    return FunctionMetrics.builder()
                            .id(UUID.randomUUID().toString())
                            .functionId(functionId)
                            .functionName(function.getName())
                            .appName(function.getAppName())
                            .userId(function.getUserId())
                            .build();
                });
        metrics.recordExecution(executionTimeMs, successful);
        return metricsRepository.save(metrics);
    }

    @Override
    @Transactional(readOnly = true)
    public FunctionMetrics getFunctionMetrics(String functionId) {
        return metricsRepository.findByFunctionId(functionId)
                .orElseThrow(() -> new ResourceNotFoundException("Function metrics", functionId));
    }

}