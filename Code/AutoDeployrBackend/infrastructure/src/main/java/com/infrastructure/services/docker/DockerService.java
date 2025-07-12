package com.infrastructure.services.docker;

import com.domain.entities.Container;
import com.domain.entities.FunctionExecutionResult;
import com.domain.entities.Function;
import com.domain.exceptions.BusinessRuleException;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.exceptions.ValidationException;
import com.domain.repositories.IApplicationMetadataRepository;
import com.domain.repositories.IFunctionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.infrastructure.exceptions.DockerException;
import com.infrastructure.services.config.EnvironmentVariableService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Optional;

/**
 * Service for Docker operations with secure environment variables
 */
@Service
@RequiredArgsConstructor
public class DockerService {
    private static final Logger logger = LoggerFactory.getLogger(DockerService.class);
    private static final String IMAGE_PREFIX = "autodeployr";

    private final DockerClient dockerClient;
    private final ObjectMapper objectMapper;
    private final IApplicationMetadataRepository metadataRepository;
    private final EnvironmentVariableService environmentVariableService;
    private final IFunctionRepository functionRepository;

    /**
     * Check if an image exists
     *
     * @param imageName Image name to check
     * @return True if the image exists
     * @throws DockerException If the check fails
     */
    public boolean isImageExists(String imageName) {
        try {
            return dockerClient.listImagesCmd()
                    .withImageNameFilter(imageName)
                    .exec()
                    .stream()
                    .anyMatch(image -> {
                        if (image.getRepoTags() == null) {
                            return false;
                        }
                        for (String tag : image.getRepoTags()) {
                            if (tag.equals(imageName) || tag.startsWith(imageName + ":")) {
                                return true;
                            }
                        }
                        return false;
                    });
        } catch (Exception e) {
            logger.error("Error checking if image exists: {}", e.getMessage());
            throw new DockerException("listImages", "Failed to check if image exists: " + e.getMessage(), e);
        }
    }

    /**
     * Build a Docker image for a function
     *
     * @param appName Name of the application
     * @param functionName Name of the function
     * @param buildPath Path to the build directory
     * @param environmentVariables Environment variables map
     * @return Container object
     * @throws ResourceNotFoundException If build path doesn't exist
     * @throws BusinessRuleException If build process fails
     */
    public Container buildImage(String appName, String functionName, String buildPath, Map<String, String> environmentVariables) {
        try {
            logger.info("Building Docker image for function: {}/{}", appName, functionName);

            // Create image tag
            String normalizedAppName = sanitizeForDockerTag(appName);
            String normalizedFunctionName = sanitizeForDockerTag(functionName);
            String userId = extractUserIdFromBuildPath(buildPath);
            
            // REQUIRE userId for new deployments to prevent conflicts between users
            if (userId == null || userId.isEmpty() || "unknown".equals(userId)) {
                throw new ValidationException("userId", "User ID is required for Docker image building. Build path must follow format: /path/to/build/{userId}/{appName}/{functionName}");
            }
            String imageTag;
            if (userId != null && !userId.isEmpty()) {
                // Format: autodeployr-userId-appName-functionName
                imageTag = String.format("%s-%s-%s-%s", IMAGE_PREFIX, userId.toLowerCase(), normalizedAppName, normalizedFunctionName);
                logger.info("Using user-specific image tag: {}", imageTag);
            } else {
                imageTag = String.format("%s-%s-%s", IMAGE_PREFIX, normalizedAppName, normalizedFunctionName);
                logger.info("Using legacy image tag (no userId): {}", imageTag);
            }
            
            logger.info("Using image tag: {}", imageTag);
            try {
                if (isImageExists(imageTag)) {
                    logger.info("Found existing image with tag '{}', cleaning up before rebuild", imageTag);
                    dockerClient.removeImageCmd(imageTag)
                        .withForce(true)
                        .exec();
                    logger.info("Successfully removed old image with tag: {}", imageTag);
                }
            } catch (Exception e) {
                logger.warn("Error removing existing image with tag {}: {}. Continuing with build.", imageTag, e.getMessage());
            }

            File buildDir = new File(buildPath);
            File dockerFile = new File(buildDir, "Dockerfile");

            if (!dockerFile.exists()) {
                throw new ResourceNotFoundException("Dockerfile not found in build path: " + buildPath);
            }
            BuildImageResultCallback callback = new BuildImageResultCallback() {
                @Override
                public void onNext(com.github.dockerjava.api.model.BuildResponseItem item) {
                    if (item.getStream() != null) {
                        logger.info("Docker build stream: {}", item.getStream().trim());
                    }
                    if (item.getErrorDetail() != null) {
                        logger.error("Docker build error detail: {}", 
                            item.getErrorDetail().getMessage());
                    }
                    if (item.getError() != null) {
                        logger.error("Docker build error: {}", item.getError());
                    }
                    super.onNext(item);
                }
                
                @Override
                public void onError(Throwable throwable) {
                    logger.error("Docker build callback error: {}", throwable.getMessage(), throwable);
                    super.onError(throwable);
                }
            };

            String imageId = dockerClient.buildImageCmd()
                    .withDockerfile(dockerFile)
                    .withPull(true)
                    .withNoCache(false)
                    .withTags(Collections.singleton(imageTag))
                    .exec(callback)
                    .awaitImageId();

            logger.info("Successfully built Docker image with ID: {} and tag: {} for function: {}/{}", 
                imageId, imageTag, normalizedAppName, normalizedFunctionName);
            try {
                logger.debug("Checking for very recent dangling images created during this build...");
                List<com.github.dockerjava.api.model.Image> danglingImages = dockerClient.listImagesCmd()
                    .withDanglingFilter(true)
                    .exec();
                
                long currentTime = System.currentTimeMillis();
                int cleanedCount = 0;
                for (com.github.dockerjava.api.model.Image image : danglingImages) {
                    try {
                        if (image.getCreated() != null) {
                            long imageCreatedTime = image.getCreated() * 1000;
                            long timeDiff = currentTime - imageCreatedTime;
                            if (timeDiff <= 120000) { // 2 minutes
                                dockerClient.removeImageCmd(image.getId())
                                    .withForce(true)
                                    .exec();
                                cleanedCount++;
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to remove dangling image {}: {}", image.getId(), e.getMessage());
                    }
                }
                
                if (cleanedCount > 0) {
                    logger.info("Cleaned up {} recent dangling images from this build", cleanedCount);
                }
            } catch (Exception e) {
                logger.warn("Error cleaning up dangling images: {}. Continuing execution.", e.getMessage());
            }

            // Store environment variables in the secure database storage
            if (environmentVariables != null && !environmentVariables.isEmpty()) {
                try {
                    String envUserId = userId != null ? userId : "unknown";
                    String buildPathUserId = getOwnerIdFromBuildPath(buildPath);
                    if (buildPathUserId != null && !buildPathUserId.trim().isEmpty() && !"unknown".equals(buildPathUserId)) {
                        envUserId = buildPathUserId;
                        logger.info("Using userId '{}' from build path for environment variables", envUserId);
                    } else {
                        try {
                            List<Function> functions = functionRepository.findByAppName(normalizedAppName);
                            if (functions != null && !functions.isEmpty()) {
                                for (Function function : functions) {
                                    if (function.getUserId() != null && !function.getUserId().trim().isEmpty()) {
                                        envUserId = function.getUserId();
                                        logger.info("Using userId '{}' from existing function for environment variables", envUserId);
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("Error finding user ID from functions: {}", e.getMessage());
                        }
                    }
                    String dbAppName = normalizedAppName.replace("-", "_");
                    Map<String, String> existingVars = environmentVariableService.getEnvironmentVariables(dbAppName, envUserId);
                    if (existingVars == null || existingVars.isEmpty() || 
                        !environmentVariables.keySet().equals(existingVars.keySet())) {
                        
                        logger.info("Storing {} environment variables for app '{}' with userId '{}'", 
                            environmentVariables.size(), dbAppName, envUserId);
                            
                        int storedCount = environmentVariableService.storeEnvironmentVariables(
                                dbAppName,
                                envUserId,
                                environmentVariables
                        );
                        logger.info("Securely stored {} environment variables for app: {}, userId: {}",
                                storedCount, dbAppName, envUserId);
                    } else {
                        logger.info("Environment variables already exist for app '{}' and user '{}', not storing again", 
                            dbAppName, envUserId);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to store environment variables for app {}: {}",
                            appName, e.getMessage());
                }
            }

            // Return container with the image tag as ID for easier reference
            return new Container(imageTag, functionName);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error creating Docker image for {}/{}: {}", appName, functionName, e.getMessage(), e);
            throw new BusinessRuleException("Failed to build Docker image for function: " + appName + "/" + functionName + " - " + e.getMessage());
        }
    }

    /**
     * Extract owner/user ID from build path
     * This is a temporary solution to get the user ID until proper propagation is implemented
     */
    private String getOwnerIdFromBuildPath(String buildPath) {
        try {
            File buildDir = new File(buildPath);
            String appName = buildDir.getParentFile().getName();
            String rootBuildDir = buildDir.getParentFile().getParentFile().getPath();

            var metadata = metadataRepository.readMetadata(rootBuildDir + File.separator + appName);
            if (metadata != null && metadata.getOwnerId() != null && !metadata.getOwnerId().trim().isEmpty()) {
                logger.info("Found owner ID from metadata: {}", metadata.getOwnerId());
                return metadata.getOwnerId();
            } else {
                logger.warn("Owner ID not found in metadata or is empty");
            }
        } catch (Exception e) {
            logger.warn("Could not extract owner ID from metadata: {}", e.getMessage());
        }
        try {
            File buildDir = new File(buildPath);
            String functionName = buildDir.getName();
            String appName = buildDir.getParentFile().getName();
            
            // find the function in the repository
            List<Function> functions = functionRepository.findByAppName(appName);
            if (functions != null && !functions.isEmpty()) {
                for (Function function : functions) {
                    if (function.getName().equalsIgnoreCase(functionName) && 
                        function.getUserId() != null && !function.getUserId().trim().isEmpty()) {
                        logger.info("Found user ID from function repository: {}", function.getUserId());
                        return function.getUserId();
                    }
                }
                if (!functions.isEmpty() && functions.get(0).getUserId() != null && 
                    !functions.get(0).getUserId().trim().isEmpty()) {
                    logger.info("Using user ID from first function in app: {}", functions.get(0).getUserId());
                    return functions.get(0).getUserId();
                }
            }
        } catch (Exception e) {
            logger.warn("Could not extract user ID from function repository: {}", e.getMessage());
        }
        logger.warn("Could not determine user ID, using 'unknown'");
        return "unknown";
    }

    /**
     * Execute a function in a Docker container
     *
     * @param container Container to execute
     * @param event Event data to pass to the function
     * @return Function execution result
     * @throws BusinessRuleException If execution fails
     */
    public FunctionExecutionResult executeFunction(Container container, Map<String, Object> event) {
        String containerId = null;
        try {
            if (container == null) {
                throw new ValidationException("container", "Container cannot be null");
            }
            if (event == null) {
                throw new ValidationException("event", "Event cannot be null");
            }

            String functionName = container.getFunctionName();
            String imageTag = container.getId();

            logger.debug("Executing function: {}, image: {}", functionName, imageTag);
            if (!isImageExists(imageTag)) {
                logger.error("Container image not found: {}", imageTag);
                if (!imageTag.startsWith(IMAGE_PREFIX)) {
                    logger.error("Image tag does not have the correct prefix '{}', actual tag: {}", 
                        IMAGE_PREFIX, imageTag);
                    String functionNameLower = functionName.toLowerCase();
                    List<String> possibleImageTags = new ArrayList<>();
                    
                    try {
                        // Use docker client to find all images with tags containing the function name
                        dockerClient.listImagesCmd().exec().stream()
                            .filter(img -> img.getRepoTags() != null && img.getRepoTags().length > 0)
                            .flatMap(img -> Arrays.stream(img.getRepoTags()))
                            .filter(tag -> tag.toLowerCase().contains(functionNameLower))
                            .forEach(possibleImageTags::add);
                            
                        logger.info("Found {} possible image tags for function '{}': {}", 
                            possibleImageTags.size(), functionName, possibleImageTags);
                            
                        if (!possibleImageTags.isEmpty()) {
                            String bestMatch = possibleImageTags.stream()
                                .filter(tag -> tag.startsWith(IMAGE_PREFIX) && 
                                      (tag.toLowerCase().endsWith(functionNameLower) || 
                                       tag.toLowerCase().contains(functionNameLower + "_")))
                                .findFirst()
                                .orElse(possibleImageTags.get(0));
                                
                            logger.info("Using image tag '{}' instead of '{}'", bestMatch, imageTag);
                            imageTag = bestMatch;
                        }
                    } catch (Exception e) {
                        logger.warn("Error searching for matching image tags: {}", e.getMessage());
                    }
                }
                if (!isImageExists(imageTag)) {
                    List<String> possibleImages = findImagesByPattern(functionName);
                    if (!possibleImages.isEmpty()) {
                        String suggestion = String.join(", ", possibleImages);
                        throw new ResourceNotFoundException("Container image", 
                            String.format("Image '%s' not found. Did you mean one of these: %s?", 
                                imageTag, suggestion));
                    }
                    
                    throw new ResourceNotFoundException("Container image", 
                        String.format("Container image '%s' for function '%s' not found. Images should follow the pattern: %s-{appName}-%s", 
                            imageTag, functionName, IMAGE_PREFIX, functionName));
                }
            }
            String eventJson = objectMapper.writeValueAsString(event);
            String userId = extractUserIdFromEvent(event);

            String appName = "unknown";
            if (imageTag.startsWith(IMAGE_PREFIX + "-")) {
                if (userId != null && !userId.isEmpty() && imageTag.contains("-" + userId + "-")) {
                    String prefix = IMAGE_PREFIX + "-" + userId + "-";
                    if (imageTag.startsWith(prefix)) {
                        String remainder = imageTag.substring(prefix.length());
                        int lastDashIndex = remainder.lastIndexOf("-");
                        if (lastDashIndex > 0) {
                            appName = remainder.substring(0, lastDashIndex);
                            logger.debug("Extracted app name from new format image tag: {}", appName);
                        } else {
                            appName = remainder;
                            logger.debug("Used fallback app name extraction: {}", appName);
                        }
                    }
                }
            }
            
            // Get environment variables
            Map<String, String> environmentVariables = new HashMap<>();
            try {
                if (userId != null && !userId.isEmpty()) {
                    // Load environment variables from the secure encrypted database only
                    environmentVariables = environmentVariableService.getEnvironmentVariables(appName, userId);
                    logger.debug("Loaded {} encrypted environment variables for app {} and user {}: {}", 
                        environmentVariables.size(), appName, userId, environmentVariables.keySet());
                    environmentVariables.put("USER_ID", userId);
                } else {
                    logger.warn("No userId found in event, cannot load user-specific environment variables for app: {}", appName);
                }
            } catch (Exception e) {
                logger.warn("Failed to load encrypted environment variables for app {} and user: {}", appName, e.getMessage());
            }
            List<String> envList = new ArrayList<>();
            for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
                envList.add(entry.getKey() + "=" + entry.getValue());
                logger.debug("Adding encrypted env var to container: {}=[REDACTED]", entry.getKey());
            }
            String language = "python";
            if (event.containsKey("language") && event.get("language") != null) {
                language = event.get("language").toString().toLowerCase();
                logger.info("Using language '{}' from database (passed in event)", language);
            } else {
                language = determineLanguage(functionName, appName, imageTag);
                logger.info("Determined function language using fallback logic: {}", language);
            }
            CreateContainerResponse containerResponse;
            if ("java".equalsIgnoreCase(language)) {
                containerResponse = dockerClient.createContainerCmd(imageTag)
                    .withCmd(eventJson)
                    .withEnv(envList)
                    .withHostConfig(HostConfig.newHostConfig()
                        .withNetworkMode("bridge"))
                    .exec();
                    
                logger.debug("Created Java container without port binding");
            } else if ("csharp".equalsIgnoreCase(language)) {
                envList.add("FUNCTION_EVENT_JSON=" + eventJson);
                
                containerResponse = dockerClient.createContainerCmd(imageTag)
                    .withCmd("dotnet", "/app/" + functionName + ".dll")
                    .withEnv(envList)
                    .withHostConfig(HostConfig.newHostConfig().withNetworkMode("host"))
                    .exec();
                    
                logger.debug("Created C# CLI container with JSON passed via environment variable");
            } else if ("php".equalsIgnoreCase(language)) {
                envList.add("FUNCTION_EVENT_JSON=" + eventJson);
                
                containerResponse = dockerClient.createContainerCmd(imageTag)
                    .withCmd("php", "function.php")
                    .withEnv(envList)
                    .withHostConfig(HostConfig.newHostConfig().withNetworkMode("host"))
                    .exec();
                    
                logger.debug("Created PHP serverless container with JSON passed via environment variable");
            } else {
                containerResponse = dockerClient.createContainerCmd(imageTag)
                    .withCmd("python", "-u", "function_wrapper.py", eventJson)
                    .withEnv(envList)
                    .withHostConfig(HostConfig.newHostConfig().withNetworkMode("host"))
                    .exec();
            }

            containerId = containerResponse.getId();
            logger.debug("Created container with ID: {}", containerId);

            // Start container
            dockerClient.startContainerCmd(containerId).exec();
            logger.debug("Started container {}", containerId);

            if ("php".equalsIgnoreCase(language)) {
                logger.debug("PHP container started with serverless function execution (like Java)");
            }

            if ("java".equalsIgnoreCase(language)) {
                logger.debug("Java container started with direct function execution (no Spring Boot overhead)");
            }
            Integer exitCode;
            try {
                exitCode = dockerClient.waitContainerCmd(containerId)
                        .exec(new WaitContainerResultCallback())
                        .awaitStatusCode(90, TimeUnit.SECONDS); // Increased from 60 to 90 seconds
                
                logger.debug("Container {} exited with code: {}", containerId, exitCode);
            } catch (Exception e) {
                logger.error("Timeout waiting for container {} to complete execution: {}", containerId, e.getMessage());
                String logs = getContainerLogs(containerId);
                logger.error("Container logs before timeout: {}", logs);
                try {
                    dockerClient.inspectContainerCmd(containerId).exec();
                    logger.info("Container inspection successful, checking network details");

                    dockerClient.execCreateCmd(containerId)
                        .withCmd("sh", "-c", "wget -O- http://127.0.0.1:8081/ || echo 'Failed to connect'")
                        .exec();
                        
                    logger.info("Attempted connection test inside container");
                } catch (Exception inspectEx) {
                    logger.error("Error inspecting container: {}", inspectEx.getMessage());
                }
                
                // Return a more helpful error message
                return FunctionExecutionResult.error(500, 
                    "Function execution timed out after 90 seconds. The function may be taking too long to complete " +
                    "or might be stuck. Check for infinite loops or blocking operations. Partial logs: " + 
                    logs.substring(0, Math.min(logs.length(), 500)) + "...");
            }

            String logs = getContainerLogs(containerId);
            
            // DEBUG: Show the actual container output
            logger.info("=== CONTAINER OUTPUT START ===");
            logger.info("Container ID: {}", containerId);
            logger.info("Function Name: {}", functionName);
            logger.info("Language: {} (source: {})", language, 
                event.containsKey("language") ? "database" : "fallback determination");
            logger.info("Raw container logs:\n{}", logs);
            logger.info("=== CONTAINER OUTPUT END ===");
            if (logs.contains("name 'library' is not defined") || 
                logs.contains("ModuleNotFoundError: No module named") || 
                logs.contains("ImportError")) {
                logger.warn("Detected missing dependency in function execution: {}\n{}", functionName, logs);

                Map<String, String> errorHeaders = new HashMap<>();
                errorHeaders.put("Content-Type", "application/json");
                String errorBody = objectMapper.writeValueAsString(Map.of(
                    "error", "Dependency error in function execution",
                    "details", "The function requires additional libraries that are not available. Please add dependency information to your project (e.g., requirements.txt for Python).",
                    "raw_error", logs
                ));
                
                return FunctionExecutionResult.error(500, "Dependency error in function execution: " + logs.substring(0, Math.min(logs.length(), 200)) + "...");
            }

            try {
                String finalResultMarker = "FINAL_RESULT: ";
                int finalResultIndex = logs.lastIndexOf(finalResultMarker);
                if (finalResultIndex >= 0) {
                    String jsonResult = logs.substring(finalResultIndex + finalResultMarker.length()).trim();
                    int possibleEndIndex = jsonResult.indexOf("\n");
                    if (possibleEndIndex > 0) {
                        jsonResult = jsonResult.substring(0, possibleEndIndex).trim();
                    }
                    logger.info("Extracted JSON result from FINAL_RESULT marker: {}", jsonResult);
                    if (jsonResult.contains("FINAL_RESULT:")) {
                        logger.error("CRITICAL BUG: jsonResult still contains FINAL_RESULT prefix after extraction!");
                        logger.error("Original logs: {}", logs);
                        logger.error("jsonResult: {}", jsonResult);
                        // Clean it up manually
                        jsonResult = jsonResult.replace("FINAL_RESULT:", "").trim();
                        logger.info("Manually cleaned jsonResult: {}", jsonResult);
                    }
                    
                    if (jsonResult.isEmpty() || "{}".equals(jsonResult) || "null".equals(jsonResult)) {
                        logger.warn("JSON result is empty or null, creating default response");
                        Map<String, Object> defaultResponse = new HashMap<>();
                        defaultResponse.put("statusCode", 200);
                        defaultResponse.put("headers", Collections.singletonMap("Content-Type", "application/json"));
                        defaultResponse.put("body", Map.of("message", "Function executed successfully but returned empty result"));
                        return FunctionExecutionResult.success(200, 
                            Collections.singletonMap("Content-Type", "application/json"), 
                            Map.of("message", "Function executed successfully but returned empty result"));
                    }
                    if (jsonResult.startsWith("FINAL_RESULT:")) {
                        logger.error("ERROR: Extracted JSON still contains FINAL_RESULT prefix: {}", jsonResult);
                        jsonResult = jsonResult.substring("FINAL_RESULT:".length()).trim();
                        logger.info("Cleaned JSON after removing prefix: {}", jsonResult);
                    }
                    Map<String, Object> responseMap;
                    try {
                        responseMap = objectMapper.readValue(jsonResult,
                                new com.fasterxml.jackson.core.type.TypeReference<>() {
                                });
                        logger.info("Successfully parsed JSON into response map with keys: {}", responseMap.keySet());
                    } catch (Exception parseException) {
                        logger.error("Failed to parse extracted JSON: {} - Error: {}", jsonResult, parseException.getMessage());
                        throw parseException;
                    }
                    int statusCode = 200;
                    Map<String, String> headers = new HashMap<>();
                    Object body = null;

                    if (responseMap.containsKey("statusCode")) {
                        statusCode = (Integer) responseMap.get("statusCode");
                    }

                    if (responseMap.containsKey("headers")) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> respHeaders = (Map<String, String>) responseMap.get("headers");
                        headers = respHeaders;
                    }
                    if (responseMap.containsKey("body")) {
                        Object rawBody = responseMap.get("body");
                        logger.debug("Found body in response (type: {})", 
                            (rawBody != null ? rawBody.getClass().getName() : "null"));

                        if (rawBody instanceof String) {
                            String bodyStr = (String) rawBody;
                            bodyStr = bodyStr.trim();
                            if (bodyStr.startsWith("{") || bodyStr.startsWith("[")) {
                                try {
                                    body = objectMapper.readValue(bodyStr, Object.class);
                                    logger.debug("Successfully parsed body string as JSON object");
                                    responseMap.put("body", body);
                                } catch (Exception e) {
                                    logger.debug("Failed to parse body as JSON, using original string: {}", e.getMessage());
                                    body = rawBody;
                                }
                            } else {
                                body = rawBody;
                            }
                        } else {
                            body = rawBody;
                        }
                    } else {
                        logger.warn("No 'body' field found in response map: {}", responseMap.keySet());
                        body = Map.of("message", "Function executed successfully");
                    }
                    if (body instanceof String) {
                        String bodyStr = (String) body;
                        if (bodyStr.trim().startsWith("{") || bodyStr.trim().startsWith("[")) {
                            logger.warn("Body is still a string before returning! Attempting final conversion to JSON object...");
                            try {
                                Object jsonBody = objectMapper.readValue(bodyStr, Object.class);
                                logger.info("Successfully converted body to JSON object in final check");
                                return FunctionExecutionResult.success(statusCode, headers, jsonBody);
                            } catch (Exception e) {
                                logger.warn("Final JSON parsing attempt failed: {}", e.getMessage());
                            }
                        }
                    } else {
                        logger.debug("Body is already an object of type: {}", (body != null ? body.getClass().getName() : "null"));
                    }
                    String bodyCheck = body != null ? body.toString() : "";
                    if (bodyCheck.contains("FINAL_RESULT:")) {
                        logger.error("CRITICAL ERROR: Response body still contains FINAL_RESULT prefix: {}", bodyCheck);
                        body = Map.of("error", "Internal processing error - contact administrator");
                    }
                    
                    logger.info("Returning FunctionExecutionResult with statusCode: {}, headers: {}, body type: {}", 
                        statusCode, headers, (body != null ? body.getClass().getName() : "null"));
                    return FunctionExecutionResult.success(statusCode, headers, body);
                }
                logger.debug("No FINAL_RESULT marker found, trying to parse regular JSON output");
                String funcCompletedMarker = "=== FUNCTION WRAPPER COMPLETED WITH STATUS:";
                int funcCompletedIndex = logs.lastIndexOf(funcCompletedMarker);
                if (funcCompletedIndex > 0) {
                    String beforeCompletion = logs.substring(0, funcCompletedIndex);
                    int lastJsonStart = beforeCompletion.lastIndexOf("{");
                    if (lastJsonStart >= 0) {
                        logger.info("Found potential JSON output before completion marker");
                        String jsonPart = beforeCompletion.substring(lastJsonStart);
                        try {
                            objectMapper.readTree(jsonPart);
                            logger.info("Successfully validated JSON: {}", 
                                jsonPart.substring(0, Math.min(jsonPart.length(), 200)));
                                
                            Map<String, Object> responseMap = objectMapper.readValue(jsonPart,
                                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                                    });
                            int statusCode = 200;
                            Map<String, String> headers = new HashMap<>();
                            Object body = null;
                            
                            if (responseMap.containsKey("statusCode")) {
                                statusCode = (Integer) responseMap.get("statusCode");
                            }
                            
                            if (responseMap.containsKey("headers")) {
                                @SuppressWarnings("unchecked")
                                Map<String, String> respHeaders = (Map<String, String>) responseMap.get("headers");
                                headers = respHeaders;
                            }
                            
                            if (responseMap.containsKey("body")) {
                                Object rawBody = responseMap.get("body");
                                if (rawBody instanceof String) {
                                    String bodyStr = (String) rawBody;
                                    bodyStr = bodyStr.trim();
                                    if (bodyStr.startsWith("{") || bodyStr.startsWith("[")) {
                                        try {
                                            body = objectMapper.readValue(bodyStr, Object.class);
                                            logger.debug("Successfully parsed body string as JSON object");
                                            responseMap.put("body", body);
                                        } catch (Exception e) {
                                            logger.debug("Failed to parse body as JSON, using original string: {}", e.getMessage());
                                            body = rawBody;
                                        }
                                    } else {
                                        body = rawBody;
                                    }
                                } else {
                                    body = rawBody;
                                }
                            } else {
                                body = Map.of("message", "Function executed successfully");
                            }
                            if (body instanceof String) {
                                String bodyStr = (String) body;
                                if (bodyStr.trim().startsWith("{") || bodyStr.trim().startsWith("[")) {
                                    logger.warn("Body is still a string before returning! Attempting final conversion to JSON object...");
                                    try {
                                        Object jsonBody = objectMapper.readValue(bodyStr, Object.class);
                                        logger.info("Successfully converted body to JSON object in final check");
                                        return FunctionExecutionResult.success(statusCode, headers, jsonBody);
                                    } catch (Exception e) {
                                        logger.warn("Final JSON parsing attempt failed: {}", e.getMessage());
                                    }
                                }
                            } else {
                                logger.debug("Body is already an object of type: {}", (body != null ? body.getClass().getName() : "null"));
                            }
                            
                            return FunctionExecutionResult.success(statusCode, headers, body);
                        } catch (Exception e) {
                            logger.warn("Found potential JSON but couldn't parse it: {}", e.getMessage());
                        }
                    }
                }
                logger.debug("Attempting direct JSON parsing of container logs");
                try {
                    String cleanLogs = logs.trim();
                    Map<String, Object> responseMap = objectMapper.readValue(cleanLogs, 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    
                    logger.info("Successfully parsed logs directly as JSON with keys: {}", responseMap.keySet());
                    int statusCode = 200;
                    Map<String, String> headers = new HashMap<>();
                    Object body = null;
                    
                    if (responseMap.containsKey("statusCode")) {
                        statusCode = (Integer) responseMap.get("statusCode");
                    }
                    
                    if (responseMap.containsKey("headers")) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> respHeaders = (Map<String, String>) responseMap.get("headers");
                        if (respHeaders != null) {
                            headers.putAll(respHeaders);
                        }
                    }
                    
                    if (responseMap.containsKey("body")) {
                        Object rawBody = responseMap.get("body");
                        if (rawBody instanceof String) {
                            String bodyStr = (String) rawBody;
                            bodyStr = bodyStr.trim();
                            if (bodyStr.startsWith("{") || bodyStr.startsWith("[")) {
                                try {
                                    body = objectMapper.readValue(bodyStr, Object.class);
                                    logger.info("Successfully parsed body string as JSON object: {}", body);
                                } catch (Exception e) {
                                    logger.debug("Failed to parse body as JSON, using original string: {}", e.getMessage());
                                    body = rawBody;
                                }
                            } else {
                                body = rawBody;
                            }
                        } else {
                            body = rawBody;
                        }
                    } else {
                        body = responseMap;
                    }
                    
                    logger.info("Direct JSON parsing successful - returning response with statusCode: {}, body type: {}", 
                        statusCode, (body != null ? body.getClass().getName() : "null"));
                    
                    return FunctionExecutionResult.success(statusCode, headers, body);
                    
                } catch (Exception directParseException) {
                    logger.warn("Direct JSON parsing also failed: {}", directParseException.getMessage());
                }
                int jsonStartIndex = logs.lastIndexOf("{");
                if (jsonStartIndex >= 0) {
                    String jsonPart = logs.substring(jsonStartIndex);
                    logger.debug("Found JSON part starting at index {}: {}", jsonStartIndex, 
                        jsonPart.substring(0, Math.min(jsonPart.length(), 200)));
                        
                    Map<String, Object> responseMap = objectMapper.readValue(jsonPart,
                            new com.fasterxml.jackson.core.type.TypeReference<>() {
                            });

                    logger.debug("Successfully parsed response map with keys: {}", responseMap.keySet());
                    if (responseMap.isEmpty()) {
                        logger.warn("Parsed response map is empty, creating default response");
                        return FunctionExecutionResult.success(200,
                            Collections.singletonMap("Content-Type", "application/json"),
                            Map.of("message", "Function executed successfully but returned empty data"));
                    }
                    
                    int statusCode = 200;
                    Map<String, String> headers = new HashMap<>();
                    Object body = null;

                    if (responseMap.containsKey("statusCode")) {
                        statusCode = (Integer) responseMap.get("statusCode");
                    }

                    if (responseMap.containsKey("headers")) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> respHeaders = (Map<String, String>) responseMap.get("headers");
                        headers = respHeaders;
                    }

                    if (responseMap.containsKey("body")) {
                        Object rawBody = responseMap.get("body");
                        logger.debug("Found body in response (type: {})", 
                            (rawBody != null ? rawBody.getClass().getName() : "null"));
                        if (rawBody instanceof String) {
                            String bodyStr = (String) rawBody;
                            bodyStr = bodyStr.trim();
                            if (bodyStr.startsWith("{") || bodyStr.startsWith("[")) {
                                try {
                                    body = objectMapper.readValue(bodyStr, Object.class);
                                    logger.debug("Successfully parsed body string as JSON object");
                                    responseMap.put("body", body);
                                } catch (Exception e) {
                                    logger.debug("Failed to parse body as JSON, using original string: {}", e.getMessage());
                                    body = rawBody;
                                }
                            } else {
                                body = rawBody;
                            }
                        } else {
                            body = rawBody;
                        }
                    } else {
                        logger.warn("No 'body' field found in response map: {}", responseMap.keySet());
                        body = Map.of("message", "Function executed successfully");
                    }
                    if (body instanceof String) {
                        String bodyStr = (String) body;
                        if (bodyStr.trim().startsWith("{") || bodyStr.trim().startsWith("[")) {
                            logger.warn("Body is still a string before returning! Attempting final conversion to JSON object...");
                            try {
                                Object jsonBody = objectMapper.readValue(bodyStr, Object.class);
                                logger.info("Successfully converted body to JSON object in final check");
                                return FunctionExecutionResult.success(statusCode, headers, jsonBody);
                            } catch (Exception e) {
                                logger.warn("Final JSON parsing attempt failed: {}", e.getMessage());
                            }
                        }
                    } else {
                        logger.debug("Body is already an object of type: {}", (body != null ? body.getClass().getName() : "null"));
                    }

                    return FunctionExecutionResult.success(statusCode, headers, body);
                } else {
                    logger.warn("No JSON object found in container output");
                    if (logs.contains("=== FUNCTION WRAPPER COMPLETED WITH STATUS: 0")) {
                        logger.info("Function executed successfully based on exit code, returning default response");
                        return FunctionExecutionResult.success(200,
                            Collections.singletonMap("Content-Type", "application/json"),
                            Map.of("message", "Function executed successfully"));
                    }
                    String logs2 = logs.trim();
                    if (logs2.contains("{") && logs2.contains("}")) {
                        logger.info("Trying one final extraction approach for JSON content");
                        try {
                            int firstBrace = logs2.indexOf('{');
                            int lastBrace = logs2.lastIndexOf('}');
                            
                            if (firstBrace >= 0 && lastBrace > firstBrace) {
                                String possibleJson = logs2.substring(firstBrace, lastBrace + 1);
                                Object extractedJson = objectMapper.readValue(possibleJson, Object.class);
                                logger.info("Successfully extracted JSON content: {}", possibleJson.substring(0, Math.min(possibleJson.length(), 100)));
                                if (extractedJson instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> jsonMap = (Map<String, Object>) extractedJson;
                                    
                                    if (jsonMap.containsKey("body")) {
                                        Object jsonBody = jsonMap.get("body");
                                        if (jsonBody instanceof String) {
                                            String bodyStr = (String) jsonBody;
                                            if (bodyStr.trim().startsWith("{") || bodyStr.trim().startsWith("[")) {
                                                try {
                                                    Object parsedBody = objectMapper.readValue(bodyStr, Object.class);
                                                    jsonMap.put("body", parsedBody);
                                                    logger.info("Successfully parsed nested JSON body");
                                                } catch (Exception e) {
                                                    logger.warn("Failed to parse body as JSON: {}", e.getMessage());
                                                }
                                            }
                                        }
                                        int jsonStatusCode = 200;
                                        Map<String, String> jsonHeaders = new HashMap<>();
                                        
                                        if (jsonMap.containsKey("statusCode") && jsonMap.get("statusCode") instanceof Number) {
                                            jsonStatusCode = ((Number) jsonMap.get("statusCode")).intValue();
                                        }
                                        
                                        if (jsonMap.containsKey("headers") && jsonMap.get("headers") instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, String> headersMap = (Map<String, String>) jsonMap.get("headers");
                                            jsonHeaders = headersMap;
                                        } else {
                                            jsonHeaders.put("Content-Type", "application/json");
                                        }
                                        
                                        return FunctionExecutionResult.success(jsonStatusCode, jsonHeaders, jsonMap.get("body"));
                                    }
                                }
                                return FunctionExecutionResult.success(200,
                                    Collections.singletonMap("Content-Type", "application/json"), extractedJson);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed in final JSON extraction attempt: {}", e.getMessage());
                        }
                    }
                    logger.error("All JSON extraction attempts failed. Raw logs: {}", logs.length() > 500 ? logs.substring(0, 500) + "..." : logs);
                    return FunctionExecutionResult.error(500, "Function execution completed but no valid JSON response found. Check function implementation.");
                }

            } catch (Exception e) {
                logger.error("Error parsing container output as JSON: {}", e.getMessage());
                logger.error("Raw container logs that failed parsing: {}", logs.length() > 200 ? logs.substring(0, 200) + "..." : logs);
                String finalResultMarker = "FINAL_RESULT: ";
                int finalResultIndex = logs.lastIndexOf(finalResultMarker);
                if (finalResultIndex >= 0) {
                    String jsonResult = logs.substring(finalResultIndex + finalResultMarker.length()).trim();
                    int possibleEndIndex = jsonResult.indexOf("\n");
                    if (possibleEndIndex > 0) {
                        jsonResult = jsonResult.substring(0, possibleEndIndex).trim();
                    }
                    logger.error("Last attempt - extracted JSON from failed parsing: {}", jsonResult);
                    if (jsonResult.contains("FINAL_RESULT:")) {
                        logger.error("EMERGENCY: jsonResult still contains FINAL_RESULT prefix! Cleaning...");
                        jsonResult = jsonResult.replace("FINAL_RESULT:", "").trim();
                    }
                    if (!jsonResult.isEmpty() && !jsonResult.equals("{}")) {
                        try {
                            Object testParse = objectMapper.readValue(jsonResult, Object.class);
                            logger.info("Successfully validated emergency extraction JSON");
                            if (testParse instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> testMap = (Map<String, Object>) testParse;
                                
                                int statusCode = 200;
                                Map<String, String> headers = new HashMap<>();
                                headers.put("Content-Type", "application/json");
                                Object body = testMap.get("body");
                                
                                if (testMap.containsKey("statusCode")) {
                                    statusCode = ((Number) testMap.get("statusCode")).intValue();
                                }
                                if (testMap.containsKey("headers") && testMap.get("headers") instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, String> respHeaders = (Map<String, String>) testMap.get("headers");
                                    headers.putAll(respHeaders);
                                }
                                
                                logger.info("Emergency extraction successful - returning structured response");
                                return FunctionExecutionResult.success(statusCode, headers, body);
                            } else {
                                return FunctionExecutionResult.success(200,
                                    Collections.singletonMap("Content-Type", "application/json"), testParse);
                            }
                        } catch (Exception parseException) {
                            logger.error("Emergency JSON parsing also failed: {}", parseException.getMessage());
                        }
                    }
                }
                logger.error("ALL EXTRACTION ATTEMPTS FAILED - returning clean error response");
                return FunctionExecutionResult.error(500, "Function execution completed but response could not be parsed. Please check function implementation returns valid JSON format.");
            }

        } catch (Exception e) {
            logger.error("Error executing function: {}", e.getMessage(), e);
            return FunctionExecutionResult.error(500, "Error executing function: " + e.getMessage());
        } finally {
            if (containerId != null) {
                try {
                    dockerClient.removeContainerCmd(containerId)
                            .withForce(true)
                            .exec();
                    logger.debug("Removed container {}", containerId);
                } catch (Exception e) {
                    logger.warn("Error removing container {}: {}", containerId, e.getMessage());
                }
            }
        }
    }

    /**
     * Extract user ID from the event object if available
     */
    private String extractUserIdFromEvent(Map<String, Object> event) {
        try {
            logger.debug("Extracting userId from event with keys: {}", event.keySet());
            if (event.containsKey("requestContext")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
                if (requestContext != null) {
                    logger.debug("Found requestContext with keys: {}", requestContext.keySet());
                    if (requestContext.containsKey("userId")) {
                        String userId = requestContext.get("userId").toString();
                        logger.info("Found userId '{}' in requestContext", userId);
                        return userId;
                    }
                    if (requestContext.containsKey("authentication") || requestContext.containsKey("auth")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> auth = (Map<String, Object>) 
                            requestContext.getOrDefault("authentication", requestContext.get("auth"));
                        
                        if (auth != null) {
                            if (auth.containsKey("userId") || auth.containsKey("user_id") || auth.containsKey("id")) {
                                Object userIdObj = auth.getOrDefault("userId", 
                                    auth.getOrDefault("user_id", auth.get("id")));
                                if (userIdObj != null) {
                                    logger.info("Found userId '{}' in authentication object", userIdObj);
                                    return userIdObj.toString();
                                }
                            }
                        }
                    }
                    if (requestContext.containsKey("user")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> user = (Map<String, Object>) requestContext.get("user");
                        
                        if (user != null) {
                            if (user.containsKey("id") || user.containsKey("userId") || user.containsKey("user_id")) {
                                Object userIdObj = user.getOrDefault("id", 
                                    user.getOrDefault("userId", user.get("user_id")));
                                if (userIdObj != null) {
                                    logger.info("Found userId '{}' in user object", userIdObj);
                                    return userIdObj.toString();
                                }
                            }
                        }
                    }
                }
            }
            if (event.containsKey("headers")) {
                @SuppressWarnings("unchecked")
                Map<String, String> headers = (Map<String, String>) event.get("headers");
                if (headers != null) {
                    logger.debug("Found headers with keys: {}", headers.keySet());
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        String key = entry.getKey().toLowerCase();
                        if (key.contains("user") && key.contains("id")) {
                            String userId = entry.getValue();
                            logger.info("Found userId '{}' in headers under key '{}'", userId, entry.getKey());
                            return userId;
                        }
                    }
                    if (headers.containsKey("Authorization") || headers.containsKey("authorization")) {
                        String authHeader = headers.getOrDefault("Authorization", headers.get("authorization"));
                        String userId = extractUserFromToken(authHeader);
                        if (userId != null && !userId.trim().isEmpty()) {
                            logger.info("Extracted userId '{}' from auth token", userId);
                            return userId;
                        }
                    }
                }
            }
            if (event.containsKey("queryParameters") || event.containsKey("queryStringParameters")) {
                @SuppressWarnings("unchecked")
                Map<String, String> queryParams = (Map<String, String>) 
                    event.getOrDefault("queryParameters", event.get("queryStringParameters"));
                    
                if (queryParams != null) {
                    String userId = queryParams.getOrDefault("userId", 
                        queryParams.getOrDefault("user_id", queryParams.get("id")));
                        
                    if (userId != null && !userId.trim().isEmpty()) {
                        logger.info("Found userId '{}' in query parameters", userId);
                        return userId;
                    }
                }
            }
            if (event.containsKey("appName")) {
                String appName = event.get("appName").toString();
                try {
                    String rootBuildDir = System.getProperty("user.dir") + File.separator + "build";
                    var metadata = metadataRepository.readMetadata(rootBuildDir + File.separator + appName);
                    if (metadata != null && metadata.getOwnerId() != null && !metadata.getOwnerId().trim().isEmpty()) {
                        logger.info("Found userId '{}' in app metadata", metadata.getOwnerId());
                        return metadata.getOwnerId();
                    }
                } catch (Exception e) {
                    logger.warn("Failed to get userId from app metadata: {}", e.getMessage());
                }
            }
            
            logger.warn("No userId found in event data, using default");
        } catch (Exception e) {
            logger.warn("Error extracting user ID from event: {}", e.getMessage(), e);
        }
        try {
            List<Function> functions = functionRepository.findAll();
            if (!functions.isEmpty()) {
                for (Function function : functions) {
                    if (function.getUserId() != null && !function.getUserId().trim().isEmpty()) {
                        logger.info("Using existing userId '{}' from function repository as fallback", function.getUserId());
                        return function.getUserId();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error getting fallback user ID: {}", e.getMessage());
        }
        logger.warn("Using hardcoded user ID as ultimate fallback");
        return "16ebc1f5-81ff-4547-a2fb-9df8ed1795d4";
    }
    private String extractUserFromToken(String authHeader) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
            }
        } catch (Exception e) {
            logger.warn("Failed to extract user from token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Remove a Docker image for a function
     *
     * @param functionName Name of the function
     * @return True if successful
     * @throws BusinessRuleException If removal fails
     */
    public boolean removeImage(String functionName) {
        try {
            logger.info("Attempting to remove Docker images for function: {}", functionName);
            String normalizedFunctionName = sanitizeForDockerTag(functionName);
            logger.debug("Normalized function name for removal: {} -> {}", functionName, normalizedFunctionName);

            // Get a list of all images
            List<com.github.dockerjava.api.model.Image> images = dockerClient.listImagesCmd().exec();
            boolean foundAnyImage = false;

            // Loop through all images
            for (com.github.dockerjava.api.model.Image image : images) {
                String[] repoTags = image.getRepoTags();
                if (repoTags == null || repoTags.length == 0) {
                    continue;
                }

                // Check each repo tag
                for (String tag : repoTags) {
                    if ((tag.contains("-" + functionName) || tag.contains("-" + normalizedFunctionName)) && tag.startsWith(IMAGE_PREFIX)) {
                        logger.info("Found image with tag '{}' to remove", tag);

                        try {
                            // Force remove the image
                            dockerClient.removeImageCmd(tag)
                                    .withForce(true)
                                    .exec();
                            logger.info("Successfully removed image with tag: {}", tag);
                            foundAnyImage = true;

                            // Extract app name from the tag
                            String[] parts = tag.split("-");
                            if (parts.length >= 2) {
                                String appName = parts[1];
                            }
                        } catch (Exception e) {
                            logger.error("Failed to remove image with tag {}: {}", tag, e.getMessage());
                        }
                    }
                }
                if (image.getRepoTags() != null) {
                    for (String tag : image.getRepoTags()) {
                        if (((tag.contains("-" + functionName) || tag.contains("-" + normalizedFunctionName)) && tag.startsWith(IMAGE_PREFIX)) ||
                                tag.equals("serverless-" + functionName) || tag.equals("serverless-" + normalizedFunctionName)) {

                            try {
                                String imageId = image.getId();
                                dockerClient.removeImageCmd(imageId)
                                        .withForce(true)
                                        .exec();
                                logger.info("Successfully removed image by ID: {}", imageId);
                                foundAnyImage = true;
                            } catch (Exception e) {
                                logger.error("Failed to remove image by ID {}: {}", image.getId(), e.getMessage());
                            }

                            break;
                        }
                    }
                }
            }

            if (!foundAnyImage) {
                logger.warn("No Docker images found for function: {}", functionName);
            }
            try {
                int removedCount = cleanupDanglingImages();
                if (removedCount > 0) {
                    logger.info("Cleaned up {} dangling images after removing function images", removedCount);
                }
            } catch (Exception e) {
                logger.warn("Error cleaning up dangling images: {}", e.getMessage());
            }

            return true;

        } catch (Exception e) {
            logger.error("Error during Docker image removal for function {}: {}", functionName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get logs from a container
     *
     * @param containerId ID of the container
     * @return Container logs as a string
     * @throws DockerException If an error occurs
     */
    private String getContainerLogs(String containerId) throws DockerException {
        try {
            LogContainerCallback logCallback = new LogContainerCallback();

            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(logCallback);

            logCallback.awaitCompletion(10, TimeUnit.SECONDS);
            return logCallback.getOutput();
        } catch (Exception e) {
            throw new DockerException("logs", "Failed to get container logs: " + e.getMessage(), e);
        }
    }

    /**
     * Helper class to capture container logs
     */
    private static class LogContainerCallback extends ResultCallback.Adapter<Frame> {
        private final StringBuilder logs = new StringBuilder();

        @Override
        public void onNext(Frame frame) {
            logs.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
        }

        public String getOutput() {
            return logs.toString();
        }
    }

    /**
     * Find images that match a function name pattern
     * 
     * @param functionName The function name to look for
     * @return List of matching image tags
     */
    private List<String> findImagesByPattern(String functionName) {
        List<String> matchingImages = new ArrayList<>();
        try {
            String lowerFunctionName = functionName.toLowerCase();
            String normalizedFunctionName = sanitizeForDockerTag(functionName);
            
            dockerClient.listImagesCmd().exec().stream()
                .filter(image -> image.getRepoTags() != null)
                .forEach(image -> {
                    for (String tag : image.getRepoTags()) {
                        if (tag.startsWith(IMAGE_PREFIX + "-") && 
                            (tag.toLowerCase().endsWith("-" + lowerFunctionName) || 
                             tag.toLowerCase().endsWith("-" + normalizedFunctionName))) {
                            matchingImages.add(tag);
                        }
                    }
                });
        } catch (Exception e) {
            logger.warn("Error searching for matching images: {}", e.getMessage());
        }
        return matchingImages;
    }

    /**
     * Determine the language of a function based on various heuristics
     * 
     * @param functionName The name of the function
     * @param appName The application name
     * @param imageTag The container image tag
     * @return The detected language (java or python)
     */
    private String determineLanguage(String functionName, String appName, String imageTag) {
        if (appName.toLowerCase().contains("java") || 
            appName.toLowerCase().contains("spring") ||
            imageTag.toLowerCase().contains("java") ||
            imageTag.toLowerCase().contains("spring")) {
            
            logger.info("Detected Java language based on app/image name: {}", appName);
            return "java";
        }
        
        // Check for C# ASP.NET applications
        if (appName.toLowerCase().contains("csharp") || 
            appName.toLowerCase().contains("dotnet") ||
            appName.toLowerCase().contains("aspnet") ||
            appName.toLowerCase().contains("netcore") ||
            imageTag.toLowerCase().contains("csharp") ||
            imageTag.toLowerCase().contains("dotnet") ||
            imageTag.toLowerCase().contains("aspnet")) {
            
            logger.info("Detected C# language based on app/image name: {}", appName);
            return "csharp";
        }
        
        // Check for PHP Laravel applications
        if (appName.toLowerCase().contains("php") || 
            appName.toLowerCase().contains("laravel") ||
            imageTag.toLowerCase().contains("php") ||
            imageTag.toLowerCase().contains("laravel")) {
            
            logger.info("Detected PHP language based on app/image name: {}", appName);
            return "php";
        }
        try {
            String userId = extractUserIdFromImageTag(imageTag);
            Function foundFunction = null;
            
            logger.debug("Language detection for: functionName={}, appName={}, userId={}", 
                functionName, appName, userId);
            if (userId != null && !userId.isEmpty() && appName != null && !appName.isEmpty()) {
                Optional<Function> functionOpt = functionRepository.findByAppNameAndNameAndUserId(appName, functionName, userId);
                if (functionOpt.isPresent() && functionOpt.get().getLanguage() != null) {
                    String language = functionOpt.get().getLanguage().toLowerCase();
                    logger.info("Found function language from user-scoped app+function lookup: {}", language);
                    return language;
                }
            }
            if (userId != null && !userId.isEmpty()) {
                Optional<Function> functionOpt = functionRepository.findByNameAndUserId(functionName, userId);
                if (functionOpt.isPresent() && functionOpt.get().getLanguage() != null) {
                    String language = functionOpt.get().getLanguage().toLowerCase();
                    logger.info("Found function language from user-scoped name lookup: {}", language);
                    return language;
                }
            }
            if (appName != null && !appName.isEmpty()) {
                try {
                    Optional<Function> functionOpt = functionRepository.findByAppNameAndName(appName, functionName);
                    if (functionOpt.isPresent() && functionOpt.get().getLanguage() != null) {
                        String language = functionOpt.get().getLanguage().toLowerCase();
                        logger.info("Found function language from app-scoped lookup: {}", language);
                        return language;
                    }
                } catch (Exception e) {
                    logger.warn("App-scoped function lookup failed: {}", e.getMessage());
                }
            }
            if (appName != null && !appName.isEmpty()) {
                List<Function> functions = functionRepository.findByAppName(appName);
                if (functions != null && !functions.isEmpty()) {
                    if (userId != null && !userId.isEmpty()) {
                        for (Function func : functions) {
                            if (functionName.equals(func.getName()) && 
                                userId.equals(func.getUserId()) && 
                                func.getLanguage() != null) {
                                String language = func.getLanguage().toLowerCase();
                                logger.info("Found function language from user-specific function in app: {}", language);
                                return language;
                            }
                        }
                    }
                    for (Function func : functions) {
                        if (functionName.equals(func.getName()) && func.getLanguage() != null) {
                            String language = func.getLanguage().toLowerCase();
                            logger.info("Found function language from any function in app: {}", language);
                            return language;
                        }
                    }
                }
            }
            if (userId != null && !userId.isEmpty()) {
                List<Function> userFunctions = functionRepository.findByUserId(userId);
                if (userFunctions != null && !userFunctions.isEmpty()) {
                    for (Function func : userFunctions) {
                        if (functionName.equals(func.getName()) && func.getLanguage() != null) {
                            String language = func.getLanguage().toLowerCase();
                            logger.info("Found function language from user's functions: {}", language);
                            return language;
                        }
                    }
                }
            }
            logger.info("Could not determine function language from database using scoped lookups. " +
                "Available context: functionName={}, appName={}, userId={}", 
                functionName, appName, userId);
            
        } catch (Exception e) {
            logger.warn("Failed to determine language from function repository: {}", e.getMessage());
        }
        try {
            var metadata = metadataRepository.readMetadata(appName);
            if (metadata != null) {
                String appDir = null;
                try {
                    appDir = metadata.getAppPath();
                } catch (Exception e) {
                }
                
                if (appDir != null) {
                    File dir = new File(appDir);
                    if (dir.exists() && dir.isDirectory()) {
                        File[] csharpFiles = dir.listFiles((d, name) -> name.endsWith(".cs") || name.endsWith(".csproj"));
                        if (csharpFiles != null && csharpFiles.length > 0) {
                            logger.info("Detected C# language based on file extensions (.cs or .csproj files found)");
                            return "csharp";
                        }
                        File[] phpFiles = dir.listFiles((d, name) -> name.endsWith(".php"));
                        File composerFile = new File(dir, "composer.json");
                        File artisanFile = new File(dir, "artisan");
                        if ((phpFiles != null && phpFiles.length > 0) || composerFile.exists() || artisanFile.exists()) {
                            logger.info("Detected PHP language based on file extensions (.php files, composer.json, or artisan found)");
                            return "php";
                        }
                        File[] javaFiles = dir.listFiles((d, name) -> name.endsWith(".java"));
                        if (javaFiles != null && javaFiles.length > 0) {
                            logger.info("Detected Java language based on file extensions");
                            return "java";
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error checking app metadata: {}", e.getMessage());
        }
        
        // Default to python
        logger.info("Could not determine language using any method, defaulting to python");
        return "python";
    }
    
    /**
     * Extract userId from the image tag
     * Expected format: autodeployr-userId-appName-functionName
     * 
     * @param imageTag The container image tag
     * @return userId or null if not available
     */
    private String extractUserIdFromImageTag(String imageTag) {
        if (imageTag == null || !imageTag.startsWith(IMAGE_PREFIX + "-")) {
            return null;
        }
        
        try {
            String[] parts = imageTag.split("-");
            if (parts.length >= 4) {
                return parts[1];
            }
        } catch (Exception e) {
            logger.debug("Error extracting user ID from image tag: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Clean up all dangling Docker images (those with <none> tag)
     * 
     * @return Number of images removed
     */
    public int cleanupDanglingImages() {
        try {
            logger.info("Starting cleanup of dangling Docker images");
            List<com.github.dockerjava.api.model.Image> danglingImages = dockerClient.listImagesCmd()
                .withDanglingFilter(true)
                .exec();
            
            int danglingCount = danglingImages.size();
            logger.info("Found {} dangling images to clean up", danglingCount);
            
            if (danglingCount > 0) {
                int removedCount = 0;
                long spaceReclaimed = 0;
                
                for (com.github.dockerjava.api.model.Image image : danglingImages) {
                    try {
                        long imageSize = image.getSize() != null ? image.getSize() : 0;
                        dockerClient.removeImageCmd(image.getId())
                            .withForce(true)
                            .exec();
                            
                        removedCount++;
                        spaceReclaimed += imageSize;
                        
                        logger.debug("Removed dangling image: {}", image.getId());
                    } catch (Exception e) {
                        logger.warn("Failed to remove dangling image {}: {}", image.getId(), e.getMessage());
                    }
                }
                
                logger.info("Successfully removed {} dangling images, reclaimed approximately {} bytes of disk space",
                    removedCount, spaceReclaimed);
                return removedCount;
            } else {
                logger.info("No dangling images found to clean up");
                return 0;
            }
        } catch (Exception e) {
            logger.error("Error cleaning up dangling images: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Sanitize a string to be valid for Docker image tags
     * Docker tags must be lowercase and can only contain letters, digits, underscores, periods and dashes
     * Must not start or end with a separator
     *
     * @param input The input string to sanitize
     * @return A sanitized string valid for Docker tags
     */
    public String sanitizeForDockerTag(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "unknown";
        }
        String sanitized = input.toLowerCase()
            .replaceAll("[@#$%^&*()+={}\\[\\]|\\\\:;\"'<>?,/]", "") // Remove special characters
            .replaceAll("\\s+", "_") // Replace spaces with underscores
            .replaceAll("[^a-z0-9._-]", "") // Keep only valid characters
            .replaceAll("_{2,}", "_") // Replace multiple underscores with single
            .replaceAll("^[._-]+|[._-]+$", ""); // Remove leading/trailing separators
        if (sanitized.isEmpty()) {
            return "function";
        }
        if (sanitized.matches("^[._-].*")) {
            sanitized = "fn_" + sanitized;
        }
        
        return sanitized;
    }

    /**
     * Extract userId from the build path
     * Expected format: /path/to/build/{userId}/{appName}/{functionName}
     *
     * @param buildPath Function build path
     * @return userId or "unknown" if not available
     */
    private String extractUserIdFromBuildPath(String buildPath) {
        File buildDir = new File(buildPath);
        File appDir = buildDir.getParentFile();
        
        if (appDir == null) {
            logger.warn("Invalid build path structure, couldn't extract appDir");
            return "unknown";
        }
        
        File userDir = appDir.getParentFile();
        if (userDir == null) {
            logger.warn("Invalid build path structure, couldn't extract userDir");
            return "unknown";
        }
        
        String possibleUserId = userDir.getName();
        logger.debug("Extracted userId from build path: {}", possibleUserId);
        return possibleUserId;
    }

    /**
     * Remove Docker images for a specific user and function to prevent cross-user interference
     *
     * @param functionName Name of the function whose images should be removed
     * @param userId User ID to ensure we only remove images belonging to this user
     * @return true if at least one image was removed, false otherwise
     * @throws BusinessRuleException If removal fails
     */
    public boolean removeImageForUser(String functionName, String userId) {
        try {
            if (functionName == null || functionName.trim().isEmpty()) {
                throw new ValidationException("functionName", "Function name cannot be empty");
            }
            if (userId == null || userId.trim().isEmpty()) {
                throw new ValidationException("userId", "User ID cannot be empty");
            }

            logger.info("Removing Docker images for function: {} belonging to user: {}", functionName, userId);

            boolean anyRemoved = false;
            String normalizedFunctionName = sanitizeForDockerTag(functionName);
            String normalizedUserId = userId.toLowerCase();

            try {
                List<com.github.dockerjava.api.model.Image> allImages = dockerClient.listImagesCmd().exec();
                
                for (com.github.dockerjava.api.model.Image image : allImages) {
                    if (image.getRepoTags() != null) {
                        for (String tag : image.getRepoTags()) {
                            if (tag.startsWith(IMAGE_PREFIX + "-" + normalizedUserId + "-") && 
                                (tag.toLowerCase().contains(functionName.toLowerCase()) || tag.toLowerCase().contains(normalizedFunctionName))) {
                                
                                try {
                                    logger.info("Removing user-specific Docker image: {}", tag);
                                    dockerClient.removeImageCmd(tag)
                                        .withForce(true)
                                        .exec();
                                    anyRemoved = true;
                                    logger.info("Successfully removed image: {}", tag);
                                } catch (Exception e) {
                                    logger.warn("Failed to remove image {}: {}", tag, e.getMessage());
                                }
                            }
                        }
                    }
                }

                if (anyRemoved) {
                    logger.info("Successfully removed Docker images for function: {} and user: {}", functionName, userId);
                } else {
                    logger.info("No Docker images found for function: {} and user: {}", functionName, userId);
                }

                return anyRemoved;

            } catch (Exception e) {
                logger.error("Error listing Docker images: {}", e.getMessage(), e);
                throw new DockerException("listImages", "Failed to list Docker images: " + e.getMessage(), e);
            }

        } catch (ValidationException e) {
            throw e;
        } catch (DockerException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error removing Docker images for function {} and user {}: {}", functionName, userId, e.getMessage(), e);
            throw new BusinessRuleException("Failed to remove Docker images for function: " + functionName + " and user: " + userId + " - " + e.getMessage());
        }
    }

    /**
     * Check if a path segment is likely a Laravel route parameter
     * 
     * @param segment The path segment to check
     * @return True if it looks like a Laravel parameter
     */
    private boolean isLikelyLaravelParameter(String segment) {
        String[] commonParams = {"id", "name", "slug", "uuid", "token", "key", "code", "hash", "user", "post"};
        
        for (String param : commonParams) {
            if (segment.equals(param)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Derive the function path from container name/image tag
     * Converts function names like "closure_get_hello" to "/hello"
     * 
     * @param containerId The container ID to inspect
     * @return The derived function path, or null if derivation fails
     */
    private String deriveFunctionPathFromContainerName(String containerId) {
        try {
            InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
            String imageTag = containerInfo.getConfig().getImage();
            
            if (imageTag == null) {
                logger.warn("Could not get image tag from container: {}", containerId);
                return null;
            }
            
            logger.debug("Deriving function path from image tag: {}", imageTag);
            String[] parts = imageTag.split("-");
            if (parts.length >= 4) {
                String functionName = parts[parts.length - 1];
                logger.debug("Extracted function name: {}", functionName);
                if (functionName.startsWith("closure_")) {
                    logger.warn("Path derivation from function name is unreliable for '{}'. Should use Function entity's path instead.", functionName);
                    String pathPart = functionName.substring("closure_".length());
                    if (pathPart.startsWith("get_")) {
                        pathPart = pathPart.substring("get_".length());
                    } else if (pathPart.startsWith("post_")) {
                        pathPart = pathPart.substring("post_".length());
                    } else if (pathPart.startsWith("put_")) {
                        pathPart = pathPart.substring("put_".length());
                    } else if (pathPart.startsWith("delete_")) {
                        pathPart = pathPart.substring("delete_".length());
                    }
                    if (pathPart.endsWith("_get")) {
                        pathPart = pathPart.substring(0, pathPart.length() - "_get".length());
                    } else if (pathPart.endsWith("_post")) {
                        pathPart = pathPart.substring(0, pathPart.length() - "_post".length());
                    } else if (pathPart.endsWith("_put")) {
                        pathPart = pathPart.substring(0, pathPart.length() - "_put".length());
                    } else if (pathPart.endsWith("_delete")) {
                        pathPart = pathPart.substring(0, pathPart.length() - "_delete".length());
                    }
                    String derivedPath;
                    if (pathPart.contains("_")) {
                        String[] pathSegments = pathPart.split("_");
                        StringBuilder pathBuilder = new StringBuilder("/");
                        
                        for (int i = 0; i < pathSegments.length; i++) {
                            if (i > 0) pathBuilder.append("/");
                            if (isLikelyLaravelParameter(pathSegments[i])) {
                                pathBuilder.append("{").append(pathSegments[i]).append("}");
                            } else {
                                pathBuilder.append(pathSegments[i]);
                            }
                        }
                        derivedPath = pathBuilder.toString();
                    } else {
                        derivedPath = "/" + pathPart;
                    }
                    
                    logger.info("Derived function path '{}' from function name '{}'", derivedPath, functionName);
                    if (derivedPath.contains("{")) {
                        logger.info("Detected parameterized route: {}", derivedPath);
                    }
                    return derivedPath;
                }
                String derivedPath = "/" + functionName.toLowerCase().replace("_", "-");
                logger.info("Derived generic function path '{}' from function name '{}'", derivedPath, functionName);
                return derivedPath;
            }
            
        } catch (Exception e) {
            logger.warn("Error deriving function path from container {}: {}", containerId, e.getMessage());
        }
        
        return null;
    }
}