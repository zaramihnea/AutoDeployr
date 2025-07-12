package com.application.usecases.commandhandlers;

import com.application.dtos.response.FunctionResponse;
import com.application.exceptions.FunctionInvocationException;
import com.domain.entities.Container;
import com.domain.entities.Function;
import com.domain.entities.FunctionExecutionResult;
import com.domain.entities.User;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.exceptions.ValidationException;
import com.domain.repositories.IContainerRepository;
import com.domain.repositories.IFunctionRepository;
import com.domain.repositories.IUserRepository;
import com.application.usecases.commands.InvokeFunctionCommand;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handler for the InvokeFunctionCommand
 */
@Service
@RequiredArgsConstructor
public class InvokeFunctionCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvokeFunctionCommandHandler.class);

    private final IFunctionRepository functionRepository;
    private final IContainerRepository containerRepository;
    private final IUserRepository userRepository;

    /**
     * Handle the invoke function command
     *
     * @param command Command to handle
     * @return Function execution response
     * @throws ResourceNotFoundException If the function doesn't exist
     * @throws FunctionInvocationException If function execution fails
     */
    public FunctionResponse handle(InvokeFunctionCommand command) {
        command.validate();

        String appName = command.getAppName();
        String functionName = command.getFunctionName();
        String userId = command.getUserId();
        String targetUsername = command.getTargetUsername();
        String requestMethod = (String) command.getEvent().getOrDefault("method", "GET");

        logger.info("Invoking function: {}/{} with method: {} for user: {} (target user: {})", 
                appName, functionName, requestMethod, 
                userId != null ? userId : "anonymous", targetUsername);

        try {
            // 1. Convert target username to userId
            String targetUserId = null;
            if (!targetUsername.equals("anonymous")) {
                Optional<User> targetUser = userRepository.findByUsername(targetUsername);
                if (targetUser.isEmpty()) {
                    throw new ResourceNotFoundException("User", targetUsername);
                }
                targetUserId = targetUser.get().getId();
                logger.info("Resolved target username '{}' to userId '{}'", targetUsername, targetUserId);
            }

            // 2. Find the function by app name, function name, and target user ID
            Optional<Function> functionOpt;
            try {
                // Use the new method that includes user ID for proper isolation
                functionOpt = functionRepository.findByAppNameAndNameAndUserId(appName, functionName, targetUserId);
            } catch (Exception e) {
                throw new FunctionInvocationException(functionName,
                        "Error retrieving function: " + e.getMessage(), e);
            }

            // Check if function exists
            if (functionOpt.isEmpty()) {
                throw new ResourceNotFoundException("Function", 
                    String.format("%s/%s for user %s", appName, functionName, targetUsername));
            }

            Function function = functionOpt.get();
            
            // Additional authorization check: ensure the function belongs to the target user
            if (targetUserId != null && !targetUserId.equals(function.getUserId())) {
                throw new ResourceNotFoundException("Function", 
                    String.format("%s/%s for user %s", appName, functionName, targetUsername));
            }

            // Check if the function supports the requested HTTP method
            if (function.getMethods() != null && !function.getMethods().isEmpty() &&
                    !function.getMethods().contains(requestMethod)) {
                throw new ValidationException("method",
                        "Function " + functionName + " does not support HTTP method: " + requestMethod +
                                ". Supported methods: " + String.join(", ", function.getMethods()));
            }

            // 3. Create container representation with method suffix for the build directory
            String containerFunctionName = functionName;
            if (function.getMethods() != null && !function.getMethods().isEmpty()) {
                containerFunctionName = functionName + "_" + function.getMethods().get(0).toLowerCase();
            }
            String normalizedAppName = containerRepository.sanitizeForDockerTag(appName);
            String normalizedFunctionName = containerRepository.sanitizeForDockerTag(containerFunctionName);
            boolean isJavaApplication = normalizedAppName.contains("java") || 
                                        normalizedAppName.contains("spring") ||
                                        (function.getLanguage() != null && 
                                         function.getLanguage().toLowerCase().equals("java"));
            String imageTag;
            if (targetUserId != null && !targetUserId.isEmpty()) {
                String sanitizedUserId = containerRepository.sanitizeForDockerTag(targetUserId);
                imageTag = "autodeployr-" + sanitizedUserId + "-" + normalizedAppName + "-" + normalizedFunctionName;
            } else {
                imageTag = "autodeployr-" + normalizedAppName + "-" + normalizedFunctionName;
            }
            
            Container container = new Container(imageTag, functionName);
            logger.info("Creating container with ID: {}", container.getId());
            container.validate();

            // 4. Execute function in container
            FunctionExecutionResult result;
            try {
                Map<String, Object> eventWithUserId = new HashMap<>(command.getEvent());
                if (targetUserId != null) {
                    eventWithUserId.put("userId", targetUserId);
                    Map<String, Object> requestContext = new HashMap<>();
                    requestContext.put("userId", targetUserId);
                    eventWithUserId.put("requestContext", requestContext);
                    logger.debug("Added userId '{}' to event for environment variable loading", targetUserId);
                }
                
                // Add language and framework information from the database
                if (function.getLanguage() != null && !function.getLanguage().trim().isEmpty()) {
                    eventWithUserId.put("language", function.getLanguage().toLowerCase());
                    logger.info("Added language '{}' from database to event for function execution", function.getLanguage());
                }
                
                if (function.getFramework() != null && !function.getFramework().trim().isEmpty()) {
                    eventWithUserId.put("framework", function.getFramework().toLowerCase());
                    logger.debug("Added framework '{}' from database to event for function execution", function.getFramework());
                }
                
                result = containerRepository.executeFunction(container, eventWithUserId);
            } catch (Exception e) {
                throw new FunctionInvocationException(appName + "/" + functionName,
                        "Error executing function: " + e.getMessage(), e);
            }

            // 5. Convert result to response
            return FunctionResponse.builder()
                    .statusCode(result.getStatusCode())
                    .headers(result.getHeaders())
                    .body(result.getBody())
                    .build();

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (FunctionInvocationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error invoking function {}/{} for user {}: {}", 
                appName, functionName, targetUsername, e.getMessage(), e);
            throw new FunctionInvocationException(appName + "/" + functionName, 
                "Unexpected error: " + e.getMessage(), e);
        }
    }
}