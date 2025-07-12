package com.application.usecases.commandhandlers;

import com.application.exceptions.CommandException;
import com.domain.entities.Function;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.repositories.IApplicationMetadataRepository;
import com.domain.repositories.IContainerRepository;
import com.domain.repositories.IFunctionRepository;
import com.application.usecases.commands.UndeployFunctionCommand;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.Optional;

/**
 * Handler for the UndeployFunctionCommand
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UndeployFunctionCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(UndeployFunctionCommandHandler.class);

    private final IFunctionRepository functionRepository;
    private final IContainerRepository containerRepository;
    private final IApplicationMetadataRepository metadataRepository;

    /**
     * Handle the undeploy function command
     *
     * @param command Command to handle
     * @return Success status
     * @throws ResourceNotFoundException If the function doesn't exist
     * @throws CommandException If undeployment fails
     */
    public boolean handle(UndeployFunctionCommand command) {
        command.validate();

        String functionName = command.getFunctionName();
        String appName = command.getAppName();
        String userId = command.getUserId();

        // Require both appName and userId
        if (appName == null || appName.isEmpty()) {
            throw new CommandException("ValidationError", "App name is required for function undeployment");
        }
        if (userId == null || userId.isEmpty()) {
            throw new CommandException("ValidationError", "User ID is required for function undeployment");
        }

        logger.info("Undeploying function: {}/{} for user: {}", appName, functionName, userId);

        try {
            // 1. Find the function using all three parameters (appName, functionName, userId)
            Optional<Function> functionOpt;
            try {
                functionOpt = functionRepository.findByAppNameAndNameAndUserId(appName, functionName, userId);
            } catch (Exception e) {
                throw new CommandException("FindFunction",
                        "Error retrieving function: " + e.getMessage(), e);
            }

            if (functionOpt.isEmpty()) {
                throw new ResourceNotFoundException("Function", appName + "/" + functionName + " for user " + userId);
            }

            Function function = functionOpt.get();
            String actualAppName = function.getAppName();

            // 2. Clean up Docker images for the specific user only
            try {
                logger.info("Cleaning up Docker images for function: {} belonging to user: {}", functionName, userId);

                boolean imageCleanupResult = containerRepository.cleanupImageForUser(functionName, userId);

                if (imageCleanupResult) {
                    logger.info("Successfully cleaned up Docker images for function: {}", functionName);
                } else {
                    logger.warn("Docker image cleanup may not have been completely successful for function: {}", functionName);
                    // Continue with undeployment even if image cleanup was not fully successful
                }
            } catch (Exception e) {
                logger.error("Error during Docker image cleanup for function {}: {}", functionName, e.getMessage());
                throw new CommandException("CleanupImage",
                        "Error cleaning up container image: " + e.getMessage(), e);
            }

            String methodSuffix = "";
            if (function.getMethods() != null && !function.getMethods().isEmpty()) {
                methodSuffix = "-" + function.getMethods().get(0);
            }
            
            // 3. Determine build directories using user-specific structure only
            String buildDir = System.getProperty("user.dir") + File.separator + "build";
            String userBuildDir = buildDir + File.separator + userId;
            String appBuildDir = userBuildDir + File.separator + actualAppName;

            String functionDir = appBuildDir + File.separator + functionName + methodSuffix;

            // 4. Update application metadata to remove this function
            try {
                metadataRepository.removeDeployedFunction(appBuildDir, functionName);
                logger.info("Removed function {} from application metadata", functionName);
            } catch (Exception e) {
                logger.warn("Error updating application metadata: {}", e.getMessage());
            }

            // 5. Cleanup build directory
            File functionBuildDir = new File(functionDir);
            if (functionBuildDir.exists()) {
                boolean deleted = deleteDirectory(functionBuildDir);
                if (!deleted) {
                    logger.warn("Could not fully delete build directory: {}", functionDir);
                }
            }

            // 6. Remove from repository
            try {
                functionRepository.delete(function);
            } catch (Exception e) {
                throw new CommandException("DeleteFunction",
                        "Error removing function from repository: " + e.getMessage(), e);
            }

            // 7. Check if the application directory is empty and remove it if it has no more functions
            try {
                File appDir = new File(appBuildDir);
                if (appDir.exists()) {
                    if (functionRepository.countByAppNameAndUserId(actualAppName, userId) == 0) {
                        logger.info("No more functions found for application {} and user {}. Removing application directory.", actualAppName, userId);
                        boolean appDirDeleted = deleteDirectory(appDir);
                        if (appDirDeleted) {
                            logger.info("Successfully removed application directory: {}", appBuildDir);
                        } else {
                            logger.warn("Failed to remove application directory: {}", appBuildDir);
                        }
                    } else {
                        logger.info("Application {} still has deployed functions for user {}. Keeping application directory.", actualAppName, userId);
                    }
                }
            } catch (Exception e) {
                logger.warn("Error checking/removing application directory: {}", e.getMessage());
            }

            logger.info("Function {}/{} successfully undeployed for user {}", actualAppName, functionName, userId);
            return true;

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (CommandException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error undeploying function {}/{} for user {}: {}", appName, functionName, userId, e.getMessage(), e);
            throw new CommandException("UndeployFunction",
                    "Unexpected error undeploying function: " + e.getMessage(), e);
        }
    }

    /**
     * Recursively delete a directory
     *
     * @param directoryToBeDeleted Directory to delete
     * @return Success status
     */
    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}