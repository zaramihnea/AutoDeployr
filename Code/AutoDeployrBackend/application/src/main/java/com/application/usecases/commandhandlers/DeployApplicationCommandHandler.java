package com.application.usecases.commandhandlers;

import com.application.dtos.response.DeploymentResponse;
import com.application.exceptions.DeploymentException;
import com.domain.entities.ApplicationAnalysisResult;
import com.domain.entities.Function;
import com.domain.entities.FunctionMetrics;
import com.domain.exceptions.BusinessRuleException;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.exceptions.ValidationException;
import com.domain.repositories.*;
import com.application.usecases.commands.DeployApplicationCommand;
import com.application.usecases.commands.UndeployFunctionCommand;
import com.application.usecases.commands.ToggleFunctionSecurityCommand;
import com.application.usecases.commandhandlers.UndeployFunctionCommandHandler;
import com.application.usecases.commandhandlers.ToggleFunctionSecurityCommandHandler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeployApplicationCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(DeployApplicationCommandHandler.class);

    private final IApplicationAnalyzerRepository applicationAnalyzer;
    private final IFunctionTransformerRepository functionTransformer;
    private final IFunctionRepository functionRepository;
    private final IContainerRepository containerRepository;
    private final IApplicationMetadataRepository metadataRepository;
    private final IFunctionMetricsRepository metricsRepository;
    private final IUserRepository userRepository;
    private final UndeployFunctionCommandHandler undeployFunctionCommandHandler;
    private final ToggleFunctionSecurityCommandHandler toggleFunctionSecurityCommandHandler;

    /**
     * Handle the deploy application command
     *
     * @param command Command to handle
     * @return Deployment response
     * @throws DeploymentException If the deployment fails
     * @throws ResourceNotFoundException If the application path doesn't exist
     * @throws ValidationException If the command is invalid
     */
    public DeploymentResponse handle(DeployApplicationCommand command) {
        String appPath = command.getAppPath();
        logger.info("Deploying application from: {}", appPath);
        if (appPath == null || appPath.trim().isEmpty()) {
            throw new ValidationException("appPath", "Application path cannot be empty");
        }

        // Validate application path exists
        File appDir = new File(appPath);
        if (!appDir.exists() || !appDir.isDirectory()) {
            throw new ResourceNotFoundException("Application directory not found: " + appPath);
        }

        try {
            // 1. Determine app name - use provided name or derive from path
            String appName = command.getAppName();
            if (appName == null || appName.trim().isEmpty()) {
                // If appName is not provided in the command, derive it from path
                appName = getAppNameFromPath(appPath);
                logger.info("Using derived application name from path: {}", appName);
            } else {
                // Use the provided app name, but sanitize it
                appName = sanitizeAppName(appName);
                logger.info("Using provided application name: {}", appName);
            }

            // 2. Create app-specific build directory
            String baseBuildDir = System.getProperty("user.dir") + File.separator + "build";
            
            // Include userId in the build path to ensure uniqueness across users
            String userId = command.getUserId();
            if (userId == null || userId.trim().isEmpty()) {
                userId = "unknown";
                logger.warn("No userId provided for application deployment, using 'unknown'");
            }
            
            // Create a user-specific directory structure: build/userId/appName
            String userBuildDir = baseBuildDir + File.separator + userId;
            String appBuildDir = userBuildDir + File.separator + appName;

            // Create user directory if it doesn't exist
            File userBuildDirFile = new File(userBuildDir);
            if (!userBuildDirFile.exists() && !userBuildDirFile.mkdirs()) {
                throw new DeploymentException("Failed to create user build directory: " + userBuildDir);
            }
            
            File appBuildDirFile = new File(appBuildDir);
            boolean isExistingApp = appBuildDirFile.exists();
            
            if (!isExistingApp && !appBuildDirFile.mkdirs()) {
                throw new DeploymentException("Failed to create app build directory: " + appBuildDir);
            }
            
            if (isExistingApp) {
                logger.info("Adding function(s) to existing application: {}", appName);
            } else {
                logger.info("Creating new application: {}", appName);
            }

            // 3. Create or update application metadata (without environment variables for security)
            try {
                if (isExistingApp) {
                    // For existing apps, just ensure metadata exists but don't overwrite
                    logger.info("Using existing application metadata for: {}", appName);
                } else {
                    // Create new metadata for new apps
                    metadataRepository.createMetadata(
                            appName,
                            appPath,
                            appBuildDir);
                    logger.info("Created new application metadata for: {}", appName);
                }
            } catch (Exception e) {
                // Domain/application layer should only use domain/application exceptions
                logger.warn("Error handling application metadata for {}: {}", appName, e.getMessage());
                // Don't fail deployment if metadata handling fails
            }

            // 4. Analyze the application
            ApplicationAnalysisResult result;
            try {
                result = applicationAnalyzer.analyzeApplication(appPath);
                logger.info("Analysis completed: found {} routes", result.getRoutes().size());
            } catch (Exception e) {
                throw new DeploymentException("Failed to analyze application: " + e.getMessage(), e);
            }

            // 5. Extract functions
            List<Function> functions;
            try {
                // For Python applications, use the advanced analyzer directly
                if ("python".equals(result.getLanguage()) && "flask".equals(result.getFramework())) {
                    logger.info("Using advanced Python analyzer for function extraction");
                    functions = applicationAnalyzer.extractPythonFunctions(appPath);
                } else if ("java".equals(result.getLanguage()) && "spring".equals(result.getFramework())) {
                    logger.info("Using advanced Java analyzer for function extraction");
                    functions = applicationAnalyzer.extractJavaFunctions(appPath);
                } else if ("php".equals(result.getLanguage()) && "laravel".equals(result.getFramework())) {
                    logger.info("Using advanced Laravel analyzer for function extraction");
                    functions = applicationAnalyzer.extractLaravelFunctions(appPath);
                } else {
                    functions = applicationAnalyzer.extractFunctions(result);
                }
                
                logger.info("Extracted {} functions", functions.size());

                if (functions.isEmpty()) {
                    throw new BusinessRuleException("No deployable functions found in application");
                }
            } catch (BusinessRuleException e) {
                throw e;
            } catch (Exception e) {
                throw new DeploymentException("Failed to extract functions: " + e.getMessage(), e);
            }

            // 6. Build and deploy each function
            List<String> deployedFunctions = new ArrayList<>();
            List<String> failedFunctions = new ArrayList<>();
            List<DeploymentResponse.DeployedFunctionInfo> deployedFunctionDetails = new ArrayList<>();

            for (Function function : functions) {
                try {
                    function.validate();

                    // Handle existing functions based on deployment context
                    if (isExistingApp) {
                        try {
                            // Check if function already exists in this app for this user
                            var existingFunctionOpt = functionRepository.findByAppNameAndNameAndUserId(
                                    appName, function.getName(), userId);
                            if (existingFunctionOpt.isPresent()) {
                                Function existingFunction = existingFunctionOpt.get();
                                
                                // Determine if this is a direct function deployment (temp directory) or zip deployment
                                boolean isDirectFunctionDeployment = appPath.contains("direct_") && appPath.contains("/tmp");
                                
                                if (isDirectFunctionDeployment) {
                                    // For direct function deployment: skip if function already exists
                                    logger.warn("Function '{}' already exists in app '{}' for user '{}'. Skipping direct function deployment.", 
                                               function.getName(), appName, userId);
                                    failedFunctions.add(function.getName() + " (already exists)");
                                    continue; // Skip this function
                                } else {
                                    // For zip/repository deployment: override existing function
                                    logger.info("Function '{}' already exists in app '{}' for user '{}'. Overriding with new version from zip/repository.", 
                                               function.getName(), appName, userId);
                                    
                                    // Undeploy the existing function first using the existing undeploy command
                                    try {
                                        UndeployFunctionCommand undeployCommand = UndeployFunctionCommand.builder()
                                                .functionName(existingFunction.getName())
                                                .appName(appName)
                                                .user(userId)
                                                .build();
                                        
                                        boolean undeploySuccess = undeployFunctionCommandHandler.handle(undeployCommand);
                                        if (undeploySuccess) {
                                            logger.info("Successfully undeployed existing function '{}' before redeployment", function.getName());
                                        } else {
                                            logger.warn("Undeploy of existing function '{}' returned false, but continuing with deployment", function.getName());
                                        }
                                        
                                    } catch (Exception undeployEx) {
                                        logger.warn("Error undeploying existing function '{}': {}. Continuing with deployment.", 
                                                   function.getName(), undeployEx.getMessage());
                                        // Continue with deployment even if cleanup fails
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("Error checking for existing function {}: {}", function.getName(), e.getMessage());
                            // Continue with deployment if check fails
                        }
                    }
                    if (function.getMethods() == null || function.getMethods().isEmpty()) {
                        List<String> defaultMethods = new ArrayList<>();
                        defaultMethods.add("GET");
                        function.setMethods(defaultMethods);
                        logger.info("Applied default GET method to function before saving: {}", function.getName());
                    }

                    // Set app name
                    function.setAppName(appName);

                    // Use the userId we already extracted earlier
                    function.setUserId(userId);

                    // Add environment variables from command to function
                    function.getEnvVars().addAll(command.getEnvironmentVariables().keySet());

                    // Generate ID if not present
                    if (function.getId() == null) {
                        function.setId(UUID.randomUUID().toString());
                    }

                    try {
                        Function savedFunction = functionRepository.save(function);
                        logger.debug("Saved function metadata: {}", function.getName());
                        
                        // Initialize metrics for the function
                        try {
                            FunctionMetrics emptyMetrics = FunctionMetrics.builder()
                                    .id(UUID.randomUUID().toString())
                                    .functionId(savedFunction.getId())
                                    .functionName(savedFunction.getName())
                                    .appName(savedFunction.getAppName())
                                    .userId(savedFunction.getUserId())
                                    .invocationCount(0)
                                    .successCount(0)
                                    .failureCount(0)
                                    .totalExecutionTimeMs(0)
                                    .minExecutionTimeMs(0)
                                    .maxExecutionTimeMs(0)
                                    .build();

                            metricsRepository.save(emptyMetrics);
                            logger.debug("Initialized empty metrics for function: {}", function.getName());
                        } catch (Exception e) {
                            logger.warn("Failed to initialize metrics for function {}: {}", function.getName(), e.getMessage());
                        }

                        // Handle making function private if requested
                        if (command.isPrivate()) {
                            try {
                                logger.info("Making function private as requested: {}", savedFunction.getName());
                                ToggleFunctionSecurityCommand securityCommand = ToggleFunctionSecurityCommand.builder()
                                        .functionId(savedFunction.getId())
                                        .userId(userId)
                                        .makePrivate(true)
                                        .build();
                                
                                Function securedFunction = toggleFunctionSecurityCommandHandler.handle(securityCommand);
                                savedFunction = securedFunction; // Update with secured version
                                logger.info("Successfully made function private with API key: {}", savedFunction.getName());
                            } catch (Exception e) {
                                logger.error("Failed to make function private {}: {}", savedFunction.getName(), e.getMessage());
                                // Continue deployment but function will remain public
                            }
                        }

                        // Build function URL 
                        // Format: /api/v1/{username}/functions/{appName}/{functionName}
                        String functionUrl = String.format("/api/v1/%s/functions/%s/%s", 
                                getUsernameFromUserId(userId), appName, savedFunction.getName());

                        // Create detailed function info
                        DeploymentResponse.DeployedFunctionInfo functionInfo = DeploymentResponse.DeployedFunctionInfo.builder()
                                .functionName(savedFunction.getName())
                                .appName(savedFunction.getAppName())
                                .functionUrl(functionUrl)
                                .isPrivate(savedFunction.isPrivate())
                                .apiKey(savedFunction.isPrivate() ? savedFunction.getApiKey() : null)
                                .supportedMethods(savedFunction.getMethods())
                                .build();

                        deployedFunctionDetails.add(functionInfo);
                        
                    } catch (Exception e) {
                        throw new DeploymentException("Failed to save function metadata: " + e.getMessage(), e);
                    }

                    String methodSuffixDir = "";
                    if (function.getMethods() != null && !function.getMethods().isEmpty()) {
                        methodSuffixDir = "-" + function.getMethods().get(0).toLowerCase();
                    }
                    String functionBuildDir = appBuildDir + File.separator + function.getName() + methodSuffixDir;
                    File functionBuildDirFile = new File(functionBuildDir);
                    if (!functionBuildDirFile.exists() && !functionBuildDirFile.mkdirs()) {
                        throw new DeploymentException("Failed to create function build directory: " + functionBuildDir);
                    }

                    // Create serverless function files
                    try {
                        boolean transformed = functionTransformer.createServerlessFunction(
                                function, appPath, functionBuildDir);

                        if (!transformed) {
                            throw new DeploymentException("Function transformation failed");
                        }
                    } catch (ValidationException e) {
                        // Re-throw domain exceptions directly
                        throw e;
                    } catch (Exception e) {
                        throw new DeploymentException("Failed to transform function: " + e.getMessage(), e);
                    }

                    // Create container for the function
                    try {
                        String methodSuffix = "";
                        if (function.getMethods() != null && !function.getMethods().isEmpty()) {
                            methodSuffix = "_" + function.getMethods().get(0).toLowerCase();
                        }
                        String containerName = function.getName() + methodSuffix;
                        function.setUserId(command.getUserId());
                        
                        containerRepository.createContainer(containerName, functionBuildDir, command.getEnvironmentVariables());
                    } catch (Exception e) {
                        throw new DeploymentException("Failed to create container: " + e.getMessage(), e);
                    }

                    // Update application metadata with this function
                    try {
                        metadataRepository.addDeployedFunction(appBuildDir, function.getName());
                    } catch (Exception e) {
                        logger.warn("Error updating metadata for function {}: {}", function.getName(), e.getMessage());
                    }

                    deployedFunctions.add(function.getName());
                    logger.info("Successfully deployed function: {}", function.getName());
                } catch (Exception e) {
                    logger.error("Error deploying function {}: {}", function.getName(), e.getMessage(), e);
                    failedFunctions.add(function.getName());
                }
            }

            if (failedFunctions.isEmpty()) {
                return DeploymentResponse.builder()
                        .status("success")
                        .deployedFunctions(deployedFunctions)
                        .deployedFunctionDetails(deployedFunctionDetails)
                        .message("Successfully deployed application: " + appName)
                        .build();
            } else if (deployedFunctions.isEmpty()) {
                throw new DeploymentException("All functions failed to deploy");
            } else {
                return DeploymentResponse.builder()
                        .status("partial")
                        .deployedFunctions(deployedFunctions)
                        .failedFunctions(failedFunctions)
                        .deployedFunctionDetails(deployedFunctionDetails)
                        .error("Some functions failed to deploy")
                        .build();
            }

        } catch (ResourceNotFoundException | ValidationException | BusinessRuleException e) {
            throw e;
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected deployment error: {}", e.getMessage(), e);
            throw new DeploymentException("Deployment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract application name from the path
     *
     * @param appPath Application path
     * @return Application name (last directory in the path)
     */
    private String getAppNameFromPath(String appPath) {
        Path path = Paths.get(appPath);
        String fileName = path.getFileName().toString();

        // Remove special characters and spaces, replace with underscores
        fileName = fileName.replaceAll("[^a-zA-Z0-9_]", "_");

        return fileName;
    }

    /**
     * Sanitize application name to ensure it meets naming requirements
     *
     * @param appName Raw application name
     * @return Sanitized application name
     */
    private String sanitizeAppName(String appName) {
        // Remove special characters and spaces, replace with underscores
        String sanitized = appName.replaceAll("[^a-zA-Z0-9_-]", "_");
        
        // Ensure it starts with a letter or number
        if (!sanitized.isEmpty() && !Character.isLetterOrDigit(sanitized.charAt(0))) {
            sanitized = "app_" + sanitized;
        }
        
        // Truncate if too long (container names have length limits)
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        
        logger.debug("Sanitized application name: {} -> {}", appName, sanitized);
        return sanitized;
    }

    /**
     * Extract username from userId
     *
     * @param userId User ID
     * @return Extracted username
     */
    private String getUsernameFromUserId(String userId) {
        try {
            return userRepository.findById(userId)
                    .map(user -> user.getUsername())
                    .orElse("unknown-user");
        } catch (Exception e) {
            logger.warn("Failed to get username for userId {}: {}", userId, e.getMessage());
            return "unknown-user";
        }
    }
}