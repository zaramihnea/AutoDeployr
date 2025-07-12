package com.infrastructure.aspects;

import com.domain.entities.Container;
import com.domain.entities.Function;
import com.domain.entities.FunctionExecutionResult;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.repositories.IFunctionRepository;
import com.domain.services.IMetricsService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
public class FunctionExecutionAspect {
    private static final Logger logger = LoggerFactory.getLogger(FunctionExecutionAspect.class);

    private final IMetricsService metricsService;
    private final IFunctionRepository functionRepository;

    @Around("execution(* com.infrastructure.repositories.ContainerRepositoryImpl.executeFunction(..))")
    public Object trackExecutionMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        // Extract container and event from arguments
        Object[] args = joinPoint.getArgs();
        Container container = (Container) args[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) args[1];
        String functionName = container.getFunctionName();
        String functionId = null;
        Function foundFunction = null;
        
        try {
            String userId = extractUserIdFromEvent(event);
            String appName = extractAppNameFromContainer(container);
            
            logger.debug("Function lookup for: functionName={}, userId={}, appName={}", 
                functionName, userId, appName);
            if (userId != null && !userId.isEmpty() && appName != null && !appName.isEmpty()) {
                Optional<Function> function = functionRepository.findByAppNameAndNameAndUserId(appName, functionName, userId);
                if (function.isPresent()) {
                    foundFunction = function.get();
                    logger.info("Found function using user-scoped app+function lookup: {} in app: {} for user: {}", 
                        functionName, appName, userId);
                }
            }
            if (foundFunction == null && userId != null && !userId.isEmpty()) {
                Optional<Function> function = functionRepository.findByNameAndUserId(functionName, userId);
                if (function.isPresent()) {
                    foundFunction = function.get();
                    logger.info("Found function using user-scoped name lookup: {} for user: {}", functionName, userId);
                }
            }
            if (foundFunction == null && appName != null && !appName.isEmpty()) {
                Optional<Function> appScopedFunction = functionRepository.findByAppNameAndName(appName, functionName);
                if (appScopedFunction.isPresent()) {
                    foundFunction = appScopedFunction.get();
                    logger.info("Found function using app-scoped lookup: {} in app: {}", functionName, appName);
                }
            }
            if (foundFunction == null && appName != null && !appName.isEmpty()) {
                List<Function> appFunctions = functionRepository.findByAppName(appName);
                if (appFunctions != null && !appFunctions.isEmpty()) {
                    if (userId != null && !userId.isEmpty()) {
                        for (Function func : appFunctions) {
                            if (functionName.equals(func.getName()) && userId.equals(func.getUserId())) {
                                foundFunction = func;
                                logger.info("Found function by searching app functions with user match: {} for user: {}", 
                                    functionName, userId);
                                break;
                            }
                        }
                    }
                    if (foundFunction == null) {
                        for (Function func : appFunctions) {
                            if (functionName.equals(func.getName())) {
                                foundFunction = func;
                                logger.info("Found function by searching app functions (no user match): {}", functionName);
                                break;
                            }
                        }
                    }
                }
            }
            if (foundFunction == null && userId != null && !userId.isEmpty()) {
                List<Function> userFunctions = functionRepository.findByUserId(userId);
                if (userFunctions != null && !userFunctions.isEmpty()) {
                    for (Function func : userFunctions) {
                        if (functionName.equals(func.getName())) {
                            foundFunction = func;
                            logger.info("Found function by searching user's functions: {} for user: {}", 
                                functionName, userId);
                            break;
                        }
                    }
                }
            }
            if (foundFunction == null) {
                String containerUserId = extractUserIdFromContainer(container);
                if (containerUserId != null && !containerUserId.isEmpty() && !containerUserId.equals(userId)) {
                    Optional<Function> function = functionRepository.findByNameAndUserId(functionName, containerUserId);
                    if (function.isPresent()) {
                        foundFunction = function.get();
                        logger.info("Found function using container-extracted userId: {} for user: {}", 
                            functionName, containerUserId);
                    }
                }
            }
            if (foundFunction == null) {
                logger.warn("Function not found using any scoped lookup strategy: {}. " +
                    "Available context: userId={}, appName={}. " +
                    "This function may not exist or may belong to a different user.", 
                    functionName, userId, appName);
                functionId = functionName;
            } else {
                functionId = foundFunction.getId();
                logger.info("Successfully resolved function ID: {} for function: {}", functionId, functionName);
            }
            
        } catch (Exception e) {
            logger.warn("Error during function lookup: {}", e.getMessage());
            functionId = functionName;
        }

        long startTime = System.currentTimeMillis();
        boolean successful = false;

        try {
            // Execute the function
            Object result = joinPoint.proceed();

            // Check if successful
            if (result instanceof FunctionExecutionResult) {
                FunctionExecutionResult executionResult = (FunctionExecutionResult) result;
                successful = executionResult.isSuccess();
            }

            return result;
        } finally {
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;

            // Record metrics
            try {
                if (functionId != null) {
                    metricsService.recordExecution(functionId, executionTime, successful);
                    logger.debug("Recorded execution metrics for function {}: {}ms, success={}",
                            functionId, executionTime, successful);
                } else {
                    logger.warn("Cannot record metrics: function ID is null");
                }
            } catch (Exception e) {
                logger.error("Error recording metrics for function {}: {}",
                        functionId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Extract user ID from the event object
     */
    private String extractUserIdFromEvent(Map<String, Object> event) {
        if (event == null) {
            return null;
        }
        
        try {
            if (event.containsKey("userId")) {
                return event.get("userId").toString();
            }
            if (event.containsKey("requestContext")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
                if (requestContext != null && requestContext.containsKey("userId")) {
                    return requestContext.get("userId").toString();
                }
            }
            if (event.containsKey("headers")) {
                @SuppressWarnings("unchecked")
                Map<String, String> headers = (Map<String, String>) event.get("headers");
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        String key = entry.getKey().toLowerCase();
                        if (key.contains("user") && key.contains("id")) {
                            return entry.getValue();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting user ID from event: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extract app name from container ID
     * Container ID format: autodeployr-[userId-]appName-functionName
     */
    private String extractAppNameFromContainer(Container container) {
        if (container == null || container.getId() == null) {
            return null;
        }
        
        try {
            String containerId = container.getId();
            
            // Expected format: autodeployr-userId-appName-functionName
            if (containerId.startsWith("autodeployr-")) {
                String[] parts = containerId.split("-");
                if (parts.length >= 4) {
                    return parts[2];
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting app name from container: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extract user ID from container ID
     * Container ID format: autodeployr-userId-appName-functionName
     */
    private String extractUserIdFromContainer(Container container) {
        if (container == null || container.getId() == null) {
            return null;
        }
        
        try {
            String containerId = container.getId();
            if (containerId.startsWith("autodeployr-")) {
                String[] parts = containerId.split("-");
                if (parts.length >= 4) {
                    return parts[1];
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting user ID from container: {}", e.getMessage());
        }
        
        return null;
    }
}