package com.infrastructure.repositories;

import com.domain.entities.Container;
import com.domain.entities.FunctionExecutionResult;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.exceptions.ValidationException;
import com.domain.repositories.IContainerRepository;
import com.infrastructure.exceptions.DockerException;
import com.infrastructure.services.docker.DockerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.Map;

/**
 * Implementation of IContainerRepository using Docker
 */
@Repository
public class ContainerRepositoryImpl implements IContainerRepository {
    private static final Logger logger = LoggerFactory.getLogger(ContainerRepositoryImpl.class);

    private final DockerService dockerService;

    public ContainerRepositoryImpl(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    @Override
    public Container createContainer(String functionName, String buildPath, Map<String, String> environmentVariables) {
        try {
            logger.info("Creating container for function: {}", functionName);
            if (functionName == null || functionName.trim().isEmpty()) {
                throw new ValidationException("functionName", "Function name cannot be empty");
            }

            if (buildPath == null || buildPath.trim().isEmpty()) {
                throw new ValidationException("buildPath", "Build path cannot be empty");
            }
            File buildDir = new File(buildPath);
            if (!buildDir.exists() || !buildDir.isDirectory()) {
                throw new ValidationException("buildPath", "Build path does not exist or is not a directory: " + buildPath);
            }
            String appName = getAppNameFromBuildPath(buildPath);

            return dockerService.buildImage(appName, functionName, buildPath, environmentVariables);
        } catch (ValidationException e) {
            throw e;
        } catch (DockerException e) {
            logger.error("Docker error creating container for function {}: {}", functionName, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error creating container for function {}: {}", functionName, e.getMessage(), e);
            throw new DockerException("build", "Failed to create container for function: " + functionName, e);
        }
    }

    @Override
    public FunctionExecutionResult executeFunction(Container container, Map<String, Object> event) {
        try {
            if (container == null) {
                throw new ValidationException("container", "Container cannot be null");
            }

            String functionName = container.getFunctionName();

            if (functionName == null || functionName.trim().isEmpty()) {
                throw new ValidationException("functionName", "Function name cannot be empty");
            }

            logger.info("Executing function in container: {}", functionName);
            String imageTag = container.getId();

            logger.debug("Original container image tag: {}", imageTag);

            if (!imageTag.equals(imageTag.toLowerCase())) {
                String normalizedTag = imageTag.toLowerCase();
                logger.info("Normalizing container image tag from '{}' to '{}'", imageTag, normalizedTag);
                imageTag = normalizedTag;
            }

            if (!dockerService.isImageExists(imageTag)) {
                logger.info("Container image not found with tag: {}", imageTag);

                String appNamePrefix = "autodeployr-";
                String userId = null;
                String appName = null;
                String extractedFunctionName = null;

                if (imageTag.startsWith(appNamePrefix)) {
                    String[] parts = imageTag.split("-");
                    if (parts.length >= 4) {
                        userId = parts[1];
                        appName = parts[2];
                        extractedFunctionName = parts[3];
                        
                        logger.debug("Extracted userId: {}, appName: {}, functionName: {} from image tag", userId, appName, extractedFunctionName);
                        if (extractedFunctionName.contains("_")) {
                            String baseFunctionName = extractedFunctionName.split("_")[0];
                            String possibleTag = String.format("%s%s-%s-%s", appNamePrefix, userId, appName, baseFunctionName);
                            
                            logger.info("Trying to find image with base function name: {}", possibleTag);
                            if (dockerService.isImageExists(possibleTag)) {
                                logger.info("Found image with base function name: {}", possibleTag);
                                imageTag = possibleTag;
                                container = new Container(imageTag, functionName);
                                return dockerService.executeFunction(container, event);
                            }
                        }
                        String[] commonSuffixes = {"_get", "_post", "_put", "_delete"};
                        for (String suffix : commonSuffixes) {
                            String baseFunctionName = extractedFunctionName.contains("_") ? 
                                extractedFunctionName.split("_")[0] : extractedFunctionName;
                            String possibleTag = String.format("%s%s-%s-%s%s", appNamePrefix, userId, appName, baseFunctionName, suffix);
                            
                            logger.info("Trying to find image with suffix: {}", possibleTag);
                            if (dockerService.isImageExists(possibleTag)) {
                                logger.info("Found image with suffix: {}", possibleTag);
                                imageTag = possibleTag;
                                container = new Container(imageTag, functionName);
                                return dockerService.executeFunction(container, event);
                            }
                        }
                    } else {
                        logger.warn("Image tag {} does not follow the expected autodeployr-userId-appName-functionName format", imageTag);
                    }
                } else {
                    logger.warn("Image tag {} does not start with expected prefix {}", imageTag, appNamePrefix);
                }
                
                // If no matching image was found after all these attempts
                throw new ResourceNotFoundException("Container image for function", 
                    String.format("Container image for function '%s' not found. Tried tag '%s' and variations. Expected format: autodeployr-userId-appName-functionName", 
                        functionName, imageTag));
            }

            return dockerService.executeFunction(container, event);
        } catch (ValidationException | ResourceNotFoundException e) {
            throw e;
        } catch (DockerException e) {
            String functionName = container != null ? container.getFunctionName() : "unknown";
            logger.error("Docker error executing function {}: {}", functionName, e.getMessage(), e);
            return FunctionExecutionResult.error("Error executing function: " + e.getMessage());
        } catch (Exception e) {
            String functionName = container != null ? container.getFunctionName() : "unknown";
            logger.error("Unexpected error executing function {}: {}", functionName, e.getMessage(), e);
            return FunctionExecutionResult.error("Unexpected error executing function: " + e.getMessage());
        }
    }

    @Override
    public boolean cleanupImage(String functionName) {
        try {
            if (functionName == null || functionName.trim().isEmpty()) {
                throw new ValidationException("functionName", "Function name cannot be empty");
            }

            logger.info("Cleaning up Docker images for function: {}", functionName);

            // Find and remove all related Docker images
            return dockerService.removeImage(functionName);
        } catch (ValidationException e) {
            throw e;
        } catch (DockerException e) {
            logger.error("Docker error cleaning up images for function {}: {}", functionName, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error cleaning up images for function {}: {}", functionName, e.getMessage(), e);
            throw new DockerException("cleanup", "Failed to clean up images for function: " + functionName, e);
        }
    }

    @Override
    public boolean cleanupImageForUser(String functionName, String userId) {
        try {
            logger.info("Cleaning up Docker images for function: {} belonging to user: {}", functionName, userId);
            return dockerService.removeImageForUser(functionName, userId);
        } catch (Exception e) {
            logger.error("Error cleaning up Docker image for function {} and user {}: {}", functionName, userId, e.getMessage());
            throw new DockerException("cleanupImageForUser", "Failed to cleanup Docker image for user", e);
        }
    }

    @Override
    public String sanitizeForDockerTag(String input) {
        return dockerService.sanitizeForDockerTag(input);
    }

    /**
     * Extract app name from the build path
     * Expected format: /path/to/build/{userId}/{appName}/{functionName}
     *
     * @param buildPath Function build path
     * @return App name
     */
    private String getAppNameFromBuildPath(String buildPath) {
        File buildDir = new File(buildPath);
        File parentDir = buildDir.getParentFile();

        if (parentDir != null) {
            return parentDir.getName();
        }
        return "app";
    }
}