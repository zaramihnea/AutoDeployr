package com.infrastructure.services.analyzer;

import com.domain.entities.ApplicationAnalysisResult;
import com.domain.entities.Route;
import com.infrastructure.exceptions.CodeAnalysisException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpClientErrorException;

import java.util.*;
import java.util.Arrays;

/**
 * Analyzer for C# ASP.NET applications
 * Delegates analysis to the external C# analyzer service running on port 5200
 */
@Component
public class CSharpApplicationAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(CSharpApplicationAnalyzer.class);
    
    private static final String CSHARP_ANALYZER_BASE_URL = "http://localhost:5200";
    private static final String ANALYZE_ENDPOINT = "/api/analyzer/analyze";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public CSharpApplicationAnalyzer() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Analyze C# ASP.NET application by calling the external C# analyzer service
     *
     * @param appPath Application path
     * @return Analysis result
     */
    public ApplicationAnalysisResult analyze(String appPath) {
        try {
            logger.info("Analyzing C# application at path: {}", appPath);
            
            // Call the external C# analyzer service
            String analyzerResponse = callCSharpAnalyzer(appPath);
            
            // Parse the response from the C# analyzer
            return parseAnalyzerResponse(analyzerResponse);
            
        } catch (Exception e) {
            logger.error("Error analyzing C# application: {}", e.getMessage(), e);
            throw new CodeAnalysisException("csharp", "Failed to analyze C# application: " + e.getMessage(), e);
        }
    }
    
    /**
     * Find routes in C# application
     */
    public List<Route> findRoutes(String appPath) {
        ApplicationAnalysisResult result = analyze(appPath);
        return result.getRoutes();
    }
    
    /**
     * Call the external C# analyzer service
     */
    private String callCSharpAnalyzer(String appPath) {
        try {
            String url = CSHARP_ANALYZER_BASE_URL + ANALYZE_ENDPOINT;
            
            // Prepare the request body
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("AppPath", appPath);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create the request entity
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            logger.info("Calling C# analyzer service at: {} with path: {}", url, appPath);
            
            // Make the HTTP request
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                HttpMethod.POST, 
                requestEntity, 
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Successfully received response from C# analyzer service");
                return response.getBody();
            } else {
                throw new CodeAnalysisException("csharp", 
                    "C# analyzer service returned status: " + response.getStatusCode());
            }
            
        } catch (ResourceAccessException e) {
            logger.error("Could not connect to C# analyzer service at {}: {}", 
                CSHARP_ANALYZER_BASE_URL, e.getMessage());
            throw new CodeAnalysisException("csharp", 
                "C# analyzer service is not available. Please ensure the service is running on port 5200.", e);
        } catch (HttpClientErrorException e) {
            logger.error("C# analyzer service returned error: {} - {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            throw new CodeAnalysisException("csharp", 
                "C# analyzer service error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("Unexpected error calling C# analyzer service: {}", e.getMessage(), e);
            throw new CodeAnalysisException("csharp", 
                "Unexpected error calling C# analyzer service: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse the response from the C# analyzer service into ApplicationAnalysisResult
     */
    private ApplicationAnalysisResult parseAnalyzerResponse(String jsonResponse) {
        try {
            JsonNode responseNode = objectMapper.readTree(jsonResponse);
            
            ApplicationAnalysisResult result = new ApplicationAnalysisResult();
            result.setLanguage("csharp");
            result.setFramework("aspnet");
            
            // Parse project info
            if (responseNode.has("projectInfo")) {
                JsonNode projectInfo = responseNode.get("projectInfo");
                result.setAppName(projectInfo.has("projectName") ? 
                    projectInfo.get("projectName").asText() : "unknown");
            }
            
            // Parse endpoints and convert to routes
            List<Route> routes = new ArrayList<>();
            if (responseNode.has("endpoints")) {
                JsonNode endpointsNode = responseNode.get("endpoints");
                for (JsonNode endpointNode : endpointsNode) {
                    Route route = convertEndpointToRoute(endpointNode);
                    if (route != null) {
                        routes.add(route);
                    }
                }
            }
            result.setRoutes(routes);
            
            // Parse dependencies
            Set<String> dependencies = new HashSet<>();
            if (responseNode.has("dependencies")) {
                JsonNode dependenciesNode = responseNode.get("dependencies");
                for (JsonNode dep : dependenciesNode) {
                    dependencies.add(dep.asText());
                }
            }
            
            // Parse environment variables
            Map<String, String> envVars = new HashMap<>();
            if (responseNode.has("environmentVariables")) {
                JsonNode envNode = responseNode.get("environmentVariables");
                envNode.fieldNames().forEachRemaining(fieldName -> {
                    envVars.put(fieldName, envNode.get(fieldName).asText());
                });
            }
            
            // Parse functions - the C# analyzer returns source code for endpoints
            Map<String, Map<String, String>> functions = new HashMap<>();
            for (Route route : routes) {
                if (route.getFunctionName() != null && route.getSource() != null) {
                    Map<String, String> functionInfo = new HashMap<>();
                    functionInfo.put("source", route.getSource());
                    functionInfo.put("file", route.getClassPath());
                    functions.put(route.getFunctionName(), functionInfo);
                }
            }
            result.setFunctions(functions);
            
            logger.info("Parsed C# analysis result: {} routes, {} dependencies, {} functions", 
                routes.size(), dependencies.size(), functions.size());
            
            return result;
            
        } catch (JsonProcessingException e) {
            logger.error("Error parsing C# analyzer response: {}", e.getMessage());
            throw new CodeAnalysisException("csharp", 
                "Error parsing C# analyzer response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert C# endpoint to Route entity
     */
    private Route convertEndpointToRoute(JsonNode endpointNode) {
        try {
            Route route = new Route();
            
            // Map the C# endpoint properties to Route properties
            route.setPath(endpointNode.has("path") ? endpointNode.get("path").asText() : null);
            
            // Set HTTP methods from the methods array
            if (endpointNode.has("methods") && endpointNode.get("methods").isArray()) {
                List<String> methods = new ArrayList<>();
                for (JsonNode methodNode : endpointNode.get("methods")) {
                    methods.add(methodNode.asText());
                }
                route.setMethods(methods);
            } else {
                route.setMethods(Arrays.asList("GET"));
            }
            
            // Extract method name from the "name" field (format: "ClassName.MethodName")
            String functionName = null;
            if (endpointNode.has("name")) {
                String fullName = endpointNode.get("name").asText();
                if (fullName.contains(".")) {
                    functionName = fullName.substring(fullName.lastIndexOf(".") + 1);
                } else {
                    functionName = fullName;
                }
            }
            route.setFunctionName(functionName);
            
            route.setSource(endpointNode.has("source") ? endpointNode.get("source").asText() : null);
            
            // Generate a file path from the className since filePath is not provided by C# analyzer
            if (endpointNode.has("className")) {
                String className = endpointNode.get("className").asText();
                route.setClassPath("Controllers/" + className + ".cs");
            } else {
                route.setClassPath("UnknownController.cs");
            }
            
            route.setHandlerMethod(functionName);
            
            // For the name field, use the function name we extracted
            if (functionName != null) {
                route.setName(functionName);
            } else if (route.getPath() != null) {
                // Generate a name from the path if no function name is available
                String generatedName = route.getPath().replaceAll("[^a-zA-Z0-9]", "_");
                if (generatedName.startsWith("_")) generatedName = generatedName.substring(1);
                if (generatedName.endsWith("_")) generatedName = generatedName.substring(0, generatedName.length() - 1);
                if (generatedName.isEmpty()) generatedName = "endpoint";
                route.setName(generatedName);
            } else {
                route.setName("endpoint");
            }
            
            // Set app name if available (Route entity requires this)
            route.setAppName("aspnet-app"); // Default app name for C# applications
            
            return route;
            
        } catch (Exception e) {
            logger.error("Error converting C# endpoint to route: {}", e.getMessage());
            return null;
        }
    }
} 