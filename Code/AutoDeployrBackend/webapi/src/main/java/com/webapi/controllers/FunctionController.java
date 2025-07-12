package com.webapi.controllers;

import com.application.dtos.request.DeployApplicationRequest;
import com.application.dtos.request.DirectFunctionDeployRequest;
import com.application.dtos.request.GitHubDeployRequest;
import com.application.dtos.response.ApiResponse;
import com.application.dtos.response.DeploymentResponse;
import com.application.dtos.response.FunctionResponse;
import com.application.dtos.response.FunctionSummaryResponse;
import com.application.dtos.response.metrics.FunctionMetricsResponse;
import com.application.usecases.commandhandlers.DeployApplicationCommandHandler;
import com.application.usecases.commandhandlers.InvokeFunctionCommandHandler;
import com.application.usecases.commandhandlers.UndeployFunctionCommandHandler;
import com.application.usecases.commandhandlers.ToggleFunctionSecurityCommandHandler;
import com.application.usecases.commands.DeployApplicationCommand;
import com.application.usecases.commands.InvokeFunctionCommand;
import com.application.usecases.commands.UndeployFunctionCommand;
import com.application.usecases.commands.ToggleFunctionSecurityCommand;
import com.application.usecases.queries.function.GetUserFunctionsQuery;
import com.application.usecases.queries.metrics.GetFunctionMetricsQuery;
import com.application.usecases.queryhandlers.function.GetUserFunctionsQueryHandler;
import com.application.usecases.queryhandlers.metrics.GetFunctionMetricsQueryHandler;
import com.domain.entities.Function;
import com.domain.entities.User;
import com.domain.repositories.IUserRepository;
import com.domain.repositories.IFunctionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infrastructure.security.UserPrincipal;
import com.infrastructure.services.deploy.DirectFunctionService;
import com.infrastructure.services.deploy.FileUploadService;
import com.infrastructure.services.deploy.GitHubService;
import com.webapi.utils.HttpRequestUtils;
import com.webapi.utils.ResponseBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Functions", description = "Operations for managing serverless functions")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class FunctionController {
    private static final Logger logger = LoggerFactory.getLogger(FunctionController.class);

    private final GetUserFunctionsQueryHandler getUserFunctionsQueryHandler;
    private final GetFunctionMetricsQueryHandler getFunctionMetricsQueryHandler;
    private final InvokeFunctionCommandHandler invokeFunctionCommandHandler;
    private final DeployApplicationCommandHandler deployApplicationCommandHandler;
    private final UndeployFunctionCommandHandler undeployFunctionCommandHandler;
    private final ToggleFunctionSecurityCommandHandler toggleFunctionSecurityCommandHandler;
    private final FileUploadService fileUploadService;
    private final DirectFunctionService directFunctionService;
    private final GitHubService gitHubService;
    private final ObjectMapper objectMapper;
    private final HttpRequestUtils httpRequestUtils;
    private final IUserRepository userRepository;
    private final IFunctionRepository functionRepository;

    /**
     * Deploy an application
     *
     * @param request Deployment request containing application path and environment variables
     * @param currentUser Currently authenticated user
     * @return Deployment response with status and deployed functions
     */
    @PostMapping("/functions/deploy")
    @Operation(summary = "Deploy an application",
            description = "Analyze application code, extract functions, and deploy to serverless platform")
    public ResponseEntity<ApiResponse<DeploymentResponse>> deployApplication(
            @Valid @RequestBody DeployApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        logger.info("Received deployment request for application: {} from user: {}",
                request.getAppPath(), currentUser.getUsername());

        DeployApplicationCommand command = DeployApplicationCommand.builder()
                .appPath(request.getAppPath())
                .environmentVariables(request.getEnvironmentVariables())
                .userId(currentUser.getId())
                .isPrivate(request.isPrivate())
                .build();

        DeploymentResponse response = deployApplicationCommandHandler.handle(command);

        if ("success".equals(response.getStatus())) {
            logger.info("Successfully deployed application with {} functions for user {}",
                    response.getDeployedFunctions().size(), currentUser.getUsername());

            return ResponseBuilder.success("Application deployed successfully", response);
        } else {
            logger.error("Deployment failed: {}", response.getError());
            return ResponseBuilder.error("Deployment failed: " + response.getError(), HttpStatus.INTERNAL_SERVER_ERROR, response);
        }
    }

    /**
     * Deploy application from zip file
     *
     * @param zipFile Uploaded zip file
     * @param appName Application name
     * @param envList Environment variables list
     * @param userId User ID
     * @param isPrivate Private flag
     * @param currentUser Currently authenticated user
     * @return Deployment response
     */
    @PostMapping(value = "/functions/deploy-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Deploy an application from zip file",
            description = "Upload a zip file, extract it and deploy by calling the command handler directly")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Application deployed successfully",
                    content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "Deployment failed",
                    content = @Content(mediaType = "application/json"))
    })
    public ResponseEntity<ApiResponse<DeploymentResponse>> deployApplicationFromZip(
            @RequestParam("file") MultipartFile zipFile,
            @RequestParam(value = "appName", required = false) String appName,
            @RequestParam(value = "env", required = false) List<String> envList,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "isPrivate", defaultValue = "false") boolean isPrivate,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String effectiveUserId = currentUser != null ? currentUser.getId() : userId;
        
        // If no user ID is available, return an error
        if (effectiveUserId == null || effectiveUserId.trim().isEmpty()) {
            return ResponseBuilder.error("User ID is required. Please log in or provide userId parameter.", 
                    HttpStatus.UNAUTHORIZED);
        }
        
        String username = currentUser != null ? currentUser.getUsername() : "unknown";
        logger.info("Received zip deployment request for file: {} from user: {} (id: {})",
                zipFile.getOriginalFilename(), username, effectiveUserId);
        String extractedDirectory = null;
        
        try {
            Map<String, String> environmentVariables = new HashMap<>();
            if (envList != null) {
                logger.info("Processing environment variables list: {}", envList);
                for (String env : envList) {
                    String[] parts = env.split("=", 2);
                    if (parts.length == 2) {
                        logger.info("Adding environment variable: {}={}", parts[0], parts[1]);
                        environmentVariables.put(parts[0], parts[1]);
                    } else {
                        logger.warn("Skipping invalid environment variable format: {}", env);
                    }
                }
                logger.info("Final environment variables map: {}", environmentVariables);
            }
            
            // Save the zip file to a temporary location and extract it
            Map<String, String> extractResult = fileUploadService.saveAndExtractZipFile(zipFile);
            extractedDirectory = extractResult.get("path");
            
            // Use provided appName if available, otherwise use the zip file name (without extension)
            String effectiveAppName = appName;
            if (effectiveAppName == null || effectiveAppName.isEmpty()) {
                effectiveAppName = extractResult.get("appName");
                logger.info("Using zip filename as application name: {}", effectiveAppName);
            }
            
            // Create deployment command with the extracted directory path
            DeployApplicationCommand command = DeployApplicationCommand.builder()
                    .appPath(extractedDirectory)
                    .appName(effectiveAppName)
                    .environmentVariables(environmentVariables)
                    .userId(effectiveUserId)
                    .isPrivate(isPrivate)
                    .build();

            DeploymentResponse response = deployApplicationCommandHandler.handle(command);

            if ("success".equals(response.getStatus())) {
                logger.info("Successfully deployed application from zip with {} functions for user {}",
                        response.getDeployedFunctions().size(), effectiveUserId);
                
                // Clean up the upload directory after successful deployment
                try {
                    // Get the parent directory of the extracted path (the upload_xxx directory)
                    File uploadDir = new File(extractedDirectory).getParentFile();
                    if (uploadDir != null && uploadDir.exists()) {
                        logger.info("Cleaning up upload directory: {}", uploadDir.getAbsolutePath());
                        fileUploadService.deleteDirectory(uploadDir);
                        logger.info("Successfully deleted upload directory");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to clean up upload directory: {}", e.getMessage());
                }
                
                return ResponseBuilder.success("Application deployed successfully from ZIP file", response);
            } else {
                logger.error("ZIP deployment failed: {}", response.getError());
                
                // Clean up the upload directory even if deployment failed
                try {
                    // Get the parent directory of the extracted path (the upload_xxx directory)
                    File uploadDir = new File(extractedDirectory).getParentFile();
                    if (uploadDir != null && uploadDir.exists()) {
                        logger.info("Cleaning up upload directory after failed deployment: {}", uploadDir.getAbsolutePath());
                        fileUploadService.deleteDirectory(uploadDir);
                        logger.info("Successfully deleted upload directory");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to clean up upload directory: {}", e.getMessage());
                }
                
                return ResponseBuilder.error("Deployment failed: " + response.getError(), 
                        HttpStatus.INTERNAL_SERVER_ERROR, response);
            }
        } catch (Exception e) {
            logger.error("Error processing zip file: {}", e.getMessage(), e);
            try {
                if (extractedDirectory != null) {
                    File uploadDir = new File(extractedDirectory).getParentFile();
                    if (uploadDir != null && uploadDir.exists()) {
                        logger.info("Cleaning up upload directory after exception: {}", uploadDir.getAbsolutePath());
                        fileUploadService.deleteDirectory(uploadDir);
                        logger.info("Successfully deleted upload directory");
                    }
                }
            } catch (Exception cleanupEx) {
                logger.warn("Failed to clean up upload directory after exception: {}", cleanupEx.getMessage());
            }
            
            return ResponseBuilder.error("Error processing zip file: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Deploy a function created directly
     *
     * @param request Direct function deployment request
     * @param currentUser Currently authenticated user
     * @return Deployment response
     */
    @PostMapping("/functions/deploy-direct")
    @Operation(summary = "Deploy a function created directly",
            description = "Create and deploy a function written directly in the frontend")
    public ResponseEntity<ApiResponse<DeploymentResponse>> deployDirectFunction(
            @Valid @RequestBody DirectFunctionDeployRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        logger.info("Received direct function deployment request from user: {}", currentUser.getUsername());

        try {
            // Process the direct function using the DirectFunctionService
            String appPath = directFunctionService.createTempApp(
                    request.getAppName(),
                    request.getLanguage(),
                    request.getFunctionCode()
            );

            // Create and handle the deployment command
            DeployApplicationCommand command = DeployApplicationCommand.builder()
                    .appPath(appPath)
                    .appName(request.getAppName())
                    .environmentVariables(request.getEnvironmentVariables())
                    .userId(currentUser.getId())
                    .isPrivate(request.isPrivate())
                    .build();

            DeploymentResponse response = deployApplicationCommandHandler.handle(command);

            if ("success".equals(response.getStatus())) {
                logger.info("Successfully deployed direct function for user {}", currentUser.getUsername());
                return ResponseBuilder.success("Function deployed successfully", response);
            } else {
                logger.error("Direct function deployment failed: {}", response.getError());
                return ResponseBuilder.error("Deployment failed: " + response.getError(), 
                        HttpStatus.INTERNAL_SERVER_ERROR, response);
            }
        } catch (Exception e) {
            logger.error("Error creating direct function: {}", e.getMessage(), e);
            return ResponseBuilder.error("Error creating direct function: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Deploy from GitHub repository
     *
     * @param request GitHub deployment request
     * @param currentUser Currently authenticated user
     * @return Deployment response
     */
    @PostMapping("/functions/deploy-github")
    @Operation(summary = "Deploy from GitHub",
            description = "Clone a GitHub repository and deploy it to the serverless platform")
    public ResponseEntity<ApiResponse<DeploymentResponse>> deployFromGitHub(
            @Valid @RequestBody GitHubDeployRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        logger.info("Received GitHub deployment request for repo: {} from user: {}",
                request.getRepositoryUrl(), currentUser.getUsername());

        try {
            // Use GitHubService to clone and deploy the repository
            DeploymentResponse response = gitHubService.cloneAndDeploy(request, currentUser.getId());

            if ("success".equals(response.getStatus())) {
                logger.info("Successfully deployed GitHub repository with {} functions for user {}",
                        response.getDeployedFunctions().size(), currentUser.getUsername());
                return ResponseBuilder.success("GitHub repository deployed successfully", response);
            } else {
                logger.error("GitHub deployment failed: {}", response.getError());
                return ResponseBuilder.error("Deployment failed: " + response.getError(), 
                        HttpStatus.INTERNAL_SERVER_ERROR, response);
            }
        } catch (Exception e) {
            logger.error("Error cloning or deploying GitHub repository: {}", e.getMessage(), e);
            return ResponseBuilder.error("Error deploying from GitHub: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Undeploy a function
     *
     * @param appName Application name
     * @param functionName Function name
     * @return Response with undeploy status
     */
    @DeleteMapping("/functions/undeploy/{appName}/{functionName}")
    @Operation(summary = "Undeploy a function",
            description = "Remove a deployed function from the serverless platform")
    public ResponseEntity<ApiResponse<Map<String, Object>>> undeployFunction(
            @PathVariable String appName,
            @PathVariable String functionName,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        logger.info("Received undeploy request for function: {}/{} from user: {}",
                appName, functionName, currentUser.getUsername());

        try {
            UndeployFunctionCommand command = UndeployFunctionCommand.builder()
                    .functionName(functionName)
                    .appName(appName)
                    .user(currentUser.getId())
                    .build();

            boolean result = undeployFunctionCommandHandler.handle(command);

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("status", result ? "success" : "failed");
            responseMap.put("message", result ? "Function undeployed successfully" : "Failed to undeploy function");

            if (result) {
                logger.info("Successfully undeployed function: {}/{}", appName, functionName);
                return ResponseBuilder.success("Function undeployed successfully", responseMap);
            } else {
                logger.error("Failed to undeploy function: {}/{}", appName, functionName);
                return ResponseBuilder.error("Failed to undeploy function", HttpStatus.INTERNAL_SERVER_ERROR, responseMap);
            }
        } catch (Exception e) {
            logger.error("Error undeploying function: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseBuilder.error("Error undeploying function: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR, errorResponse);
        }
    }

    /**
     * Invoke a function
     *
     * @param username Username of the function owner
     * @param appName Application name
     * @param functionName Function name
     * @param request HTTP request
     * @param allParams Query parameters
     * @param currentUser Currently authenticated user
     * @return Function execution result
     */
    @RequestMapping(value = "/{username}/functions/{appName}/{functionName}",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS,
                    RequestMethod.HEAD})
    @Operation(summary = "Invoke a function",
            description = "Execute a deployed serverless function with the provided request")
    public ResponseEntity<Object> invokeFunction(
            @PathVariable String username,
            @PathVariable String appName,
            @PathVariable String functionName,
            HttpServletRequest request,
            @RequestParam Map<String, String> allParams,
            @RequestBody(required = false) String body,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        String httpMethod = request.getMethod();
        
        // Handle authentication and authorization
        String userId = "anonymous";
        String authenticatedUsername = "anonymous";
        
        if (currentUser != null) {
            userId = currentUser.getId();
            authenticatedUsername = currentUser.getUsername();
            
            // Authorization check: users can only invoke their own functions
            if (!username.equals(authenticatedUsername)) {
                logger.warn("User {} attempted to invoke function belonging to user {}", 
                    authenticatedUsername, username);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Unauthorized: You can only invoke your own functions");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(errorResponse);
            }
        } else {
            // For anonymous access, we could allow it or restrict it based on requirements
            logger.info("Anonymous access to function {}/{}/{}", username, appName, functionName);
        }
        
        logger.info("Invoking function {}/{}/{} with HTTP method {} from user {}",
                username, appName, functionName, httpMethod, authenticatedUsername);

        try {
            // First check if the function exists and get its security settings
            Optional<User> targetUser = userRepository.findByUsername(username);
            if (targetUser.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "User not found: " + username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            String targetUserId = targetUser.get().getId();
            
            // Find the function to check its security settings
            Optional<Function> functionOpt = functionRepository.findByAppNameAndNameAndUserId(appName, functionName, targetUserId);
            if (functionOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Function not found: " + appName + "/" + functionName);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }

            Function function = functionOpt.get();

            // Check function security
            if (function.isPrivate()) {
                // Function is private, check for API key
                String providedApiKey = request.getHeader("X-Function-Key");
                
                if (providedApiKey == null || providedApiKey.trim().isEmpty()) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Access denied: This function is private and requires an API key");
                    errorResponse.put("hint", "Include 'X-Function-Key' header with your API key");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
                }

                // Validate API key
                if (!providedApiKey.equals(function.getApiKey())) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "Access denied: Invalid API key");
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
                }

                logger.info("API key validated for private function: {}/{}/{}", username, appName, functionName);
            } else {
                logger.info("Accessing public function: {}/{}/{}", username, appName, functionName);
            }

            // Extract headers
            Map<String, String> headers = httpRequestUtils.extractHeaders(request);
            
            // Extract query parameters - prioritize direct query string parsing
            Map<String, String> combinedQueryParams = new HashMap<>();
            if (allParams != null && !allParams.isEmpty()) {
                combinedQueryParams.putAll(allParams);
                logger.debug("Captured {} parameters from @RequestParam: {}", allParams.size(), allParams);
            }
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                logger.debug("Raw query string: {}", queryString);
                String[] pairs = queryString.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=", 2);
                    if (keyValue.length == 2) {
                        try {
                            String key = java.net.URLDecoder.decode(keyValue[0], "UTF-8");
                            String value = java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                            combinedQueryParams.put(key, value);
                            logger.debug("Added query parameter: {}={}", key, value);
                        } catch (Exception e) {
                            logger.warn("Failed to decode query parameter: {}", pair);
                        }
                    } else if (keyValue.length == 1) {
                        try {
                            String key = java.net.URLDecoder.decode(keyValue[0], "UTF-8");
                            combinedQueryParams.put(key, "");
                            logger.debug("Added query parameter with no value: {}=", key);
                        } catch (Exception e) {
                            logger.warn("Failed to decode query parameter: {}", pair);
                        }
                    }
                }
            } else {
                logger.debug("No query string found in request");
            }
            
            logger.info("Combined query parameters for function invocation: {}", combinedQueryParams);
            logger.info("Raw request URI: {}", request.getRequestURI());
            logger.info("Raw query string: {}", request.getQueryString());
            logger.info("@RequestParam allParams: {}", allParams);
            Map<String, Object> event = new HashMap<>();
            event.put("method", httpMethod);
            event.put("headers", headers);
            event.put("queryParameters", combinedQueryParams);
            if (body != null) {
                event.put("body", body);
            }
            
            logger.info("Created event with method: {}", httpMethod);

            // Create command with the event map that has the method
            InvokeFunctionCommand command = InvokeFunctionCommand.builder()
                    .functionName(functionName)
                    .appName(appName)
                    .httpMethod(httpMethod)
                    .headers(headers)
                    .queryParams(combinedQueryParams)
                    .body(body)
                    .userId(userId) // This should be the actual authenticated user id for logging
                    .event(event)
                    .targetUsername(username)
                    .build();

            FunctionResponse response = invokeFunctionCommandHandler.handle(command);
            Object responseBody = response.getBody();
            if (responseBody instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> bodyMap = (Map<String, Object>) responseBody;
                if (bodyMap.containsKey("body")) {
                    Object innerBody = bodyMap.get("body");
                    if (innerBody instanceof String) {
                        String innerBodyStr = ((String) innerBody).trim();
                        if (innerBodyStr.startsWith("{") || innerBodyStr.startsWith("[")) {
                            try {
                                innerBody = objectMapper.readValue(innerBodyStr, Object.class);
                                logger.debug("Successfully parsed inner JSON body");
                            } catch (Exception e) {
                                logger.warn("Failed to parse inner body as JSON: {}", e.getMessage());
                            }
                        }
                    }
                    responseBody = innerBody;
                }
            }
            
            // Set appropriate content type header
            org.springframework.http.HttpHeaders responseHeaders = new org.springframework.http.HttpHeaders();
            responseHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            
            // Return the response
            return new ResponseEntity<>(responseBody, responseHeaders, response.getStatusCode());
        } catch (Exception e) {
            logger.error("Error invoking function {}/{}/{}: {}", username, appName, functionName, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error invoking function: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        }
    }

    /**
     * Get user's functions
     *
     * @param currentUser Currently authenticated user
     * @return List of function summaries
     */
    @GetMapping("/functions/my-functions")
    @Operation(summary = "Get my functions", description = "Retrieve all functions owned by the current user")
    public ResponseEntity<ApiResponse<List<FunctionSummaryResponse>>> getMyFunctions(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        logger.info("Getting functions for user: {}", currentUser.getUsername());

        GetUserFunctionsQuery query = GetUserFunctionsQuery.builder()
                .userId(currentUser.getId())
                .build();

        List<FunctionSummaryResponse> functions = getUserFunctionsQueryHandler.handle(query);

        return ResponseBuilder.success("User functions retrieved successfully", functions);
    }

    /**
     * Get function metrics
     *
     * @param functionId Function ID
     * @param currentUser Currently authenticated user
     * @return Function metrics
     */
    @GetMapping("/functions/metrics/{functionId}")
    @Operation(summary = "Get function metrics", description = "Retrieve metrics for a specific function")
    public ResponseEntity<ApiResponse<FunctionMetricsResponse>> getFunctionMetrics(
            @PathVariable String functionId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        logger.info("Getting metrics for function: {} and user: {}", functionId, currentUser.getUsername());

        GetFunctionMetricsQuery query = GetFunctionMetricsQuery.builder()
                .functionId(functionId)
                .user(currentUser.getId())
                .build();

        FunctionMetricsResponse metrics = getFunctionMetricsQueryHandler.handle(query);

        return ResponseBuilder.success("Function metrics retrieved successfully", metrics);
    }

    /**
     * Toggle function security between public and private
     *
     * @param functionId Function ID to toggle
     * @param request Toggle request with security settings
     * @param currentUser Currently authenticated user
     * @return Updated function information
     */
    @PutMapping("/functions/{functionId}/security")
    @Operation(summary = "Toggle function security", description = "Make a function public or private and manage API keys")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleFunctionSecurity(
            @PathVariable String functionId,
            @RequestBody Map<String, Boolean> request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        logger.info("Toggling security for function: {} by user: {}", functionId, currentUser.getUsername());

        Boolean makePrivate = request.get("isPrivate");
        if (makePrivate == null) {
            return ResponseBuilder.error("Field 'isPrivate' is required", HttpStatus.BAD_REQUEST);
        }

        try {
            ToggleFunctionSecurityCommand command = ToggleFunctionSecurityCommand.builder()
                    .functionId(functionId)
                    .userId(currentUser.getId())
                    .makePrivate(makePrivate)
                    .build();

            Function updatedFunction = toggleFunctionSecurityCommandHandler.handle(command);

            Map<String, Object> response = new HashMap<>();
            response.put("functionId", updatedFunction.getId());
            response.put("isPrivate", updatedFunction.isPrivate());
            response.put("apiKey", updatedFunction.getApiKey());
            response.put("message", makePrivate ? "Function is now private" : "Function is now public");

            logger.info("Successfully toggled function security: functionId={}, isPrivate={}", 
                    functionId, updatedFunction.isPrivate());

            return ResponseBuilder.success("Function security updated successfully", response);

        } catch (Exception e) {
            logger.error("Error toggling function security for {}: {}", functionId, e.getMessage(), e);
            return ResponseBuilder.error("Error updating function security: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
} 