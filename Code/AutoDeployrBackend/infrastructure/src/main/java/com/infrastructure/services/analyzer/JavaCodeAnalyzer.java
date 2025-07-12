package com.infrastructure.services.analyzer;

import com.domain.entities.Function;
import com.domain.entities.Route;
import com.domain.exceptions.ValidationException;
import com.infrastructure.exceptions.CodeAnalysisException;
import com.infrastructure.exceptions.ExternalProcessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.io.IOException;

/**
 * Analyzer for Java Spring applications using the Spring application analyzer.
 */
public class JavaCodeAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(JavaCodeAnalyzer.class);

    private final String appPath;
    private final String javaExecutable;
    private final Path analyzerJarPath;
    private final ObjectMapper objectMapper;

    /**
     * Create a new Java code analyzer.
     * @param appPath Path to the application directory to be analyzed.
     */
    public JavaCodeAnalyzer(String appPath) {
        if (appPath == null || appPath.isBlank()) {
            throw new ValidationException("appPath", "Application path cannot be empty.");
        }
        Path pathObj = Paths.get(appPath);
        if (!Files.isDirectory(pathObj)) {
            throw new ValidationException("appPath", "Application path does not exist or is not a directory: " + appPath);
        }
        this.appPath = appPath;

        this.javaExecutable = findJavaExecutable();
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        this.analyzerJarPath = projectRoot.resolve("analyzers/spring-analyzer/target/spring-analyzer.jar");

        if (!Files.exists(analyzerJarPath)) {
            throw new CodeAnalysisException("java", "Spring analyzer JAR not found at resolved path: " + analyzerJarPath.toAbsolutePath());
        }

        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        logger.info("Initialized Java Spring analyzer for app path: {}", appPath);
        logger.debug("Using Java executable: {}", this.javaExecutable);
        logger.debug("Using Analyzer JAR: {}", this.analyzerJarPath.toAbsolutePath());
    }

    /**
     * Find the appropriate Java executable for the current system.
     */
    private String findJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        Path javaBin = Paths.get(javaHome, "bin", "java");

        if (Files.exists(javaBin)) {
            return javaBin.toString();
        }

        // Fallback to java in PATH
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(line -> logger.trace("java -version output: {}", line));
            }
            if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                logger.debug("Found Java executable in PATH");
                return "java";
            }
        } catch (Exception e) {
            logger.trace("Error checking java in PATH: {}", e.getMessage());
        }

        logger.error("Could not find Java executable in JAVA_HOME or PATH");
        throw new CodeAnalysisException("java", "Java executable not found");
    }

    /**
     * Analyzes the Spring Boot application and parses the results.
     * @return A List of Function objects.
     */
    public List<Function> analyze() {
        String operation = "Spring application analysis";
        logger.info("Starting {} for: {}", operation, appPath);

        String jsonResult = null;
        try {
            // 1. Execute Java analyzer JAR
            jsonResult = executeJavaAnalyzer(this.appPath);

            // 2. Log the raw JSON output
            logger.info("------------------------------------------------------");
            logger.info("--- RAW JSON RECEIVED FROM SPRING ANALYZER ---");
            logger.info("{}", jsonResult);
            logger.info("--- END RAW JSON ---");
            logger.info("------------------------------------------------------");

            // 3. Parse to JsonNode first
            JsonNode rootNode;
            try {
                rootNode = objectMapper.readTree(jsonResult);
                logger.info("Successfully parsed raw JSON into Jackson JsonNode tree.");
            } catch (Exception e) {
                logger.error("Failed to parse raw JSON string into JsonNode tree: {}", e.getMessage(), e);
                throw new CodeAnalysisException("java", "Failed basic JSON parsing: " + e.getMessage(), e);
            }

            // 4. Find endpoints node
            JsonNode endpointsNode = rootNode.path("endpoints");
            if (endpointsNode.isMissingNode() || !endpointsNode.isArray()) {
                logger.error("'endpoints' node is missing or not an array in the parsed JsonNode.");
                logger.error("Parsed Root Node Structure:\n{}", rootNode.toPrettyString());
                throw new CodeAnalysisException("java", "Invalid JSON structure from analyzer: 'endpoints' node missing or invalid.");
            }

            logger.info("'endpoints' node found in JsonNode and is an array. Size: {}", endpointsNode.size());

            // 5. Convert Spring endpoints to Functions
            List<Function> functions = new ArrayList<>();
            for (JsonNode endpointNode : endpointsNode) {
                Function function = convertEndpointToFunction(endpointNode, rootNode);
                functions.add(function);
            }

            // 6. Log the converted functions
            if (!functions.isEmpty()) {
                Function firstFunc = functions.get(0);
                logger.info("------------------------------------------------------");
                logger.info("--- First Converted Function: '{}' ---", firstFunc.getName());
                logger.info("Name: {}", firstFunc.getName());
                logger.info("Path: {}", firstFunc.getPath());
                logger.info("Methods: {}", firstFunc.getMethods());
                logger.info("Dependencies: {}", firstFunc.getDependencies());
                logger.info("Source length: {}", firstFunc.getSource() != null ? firstFunc.getSource().length() : 0);
                logger.info("------------------------------------------------------");
            }

            logger.info("Completed {} successfully. Returning {} functions.", operation, functions.size());
            return functions;

        } catch (ExternalProcessException e) {
            logger.error("External analyzer process failed during {}: {}", operation, e.getMessage());
            throw e;
        } catch (CodeAnalysisException e) {
            logger.error("Code analysis error during {}: {}", operation, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during {}: {}", operation, e.getMessage(), e);
            if (jsonResult != null) {
                logger.error("Raw JSON at time of unexpected error:\n{}", jsonResult);
            }
            throw new CodeAnalysisException("java", "Unexpected failure during " + operation + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convert a Spring endpoint JsonNode to a Function object.
     */
    private Function convertEndpointToFunction(JsonNode endpointNode, JsonNode rootNode) {
        String name = endpointNode.path("name").asText();
        String path = endpointNode.path("path").asText();
        String source = endpointNode.path("source").asText();
        String className = endpointNode.path("className").asText();
        String packageName = endpointNode.path("packageName").asText();

        // Extract methods
        List<String> methods = new ArrayList<>();
        JsonNode methodsNode = endpointNode.path("methods");
        if (methodsNode.isArray()) {
            methodsNode.forEach(method -> methods.add(method.asText()));
        }
        if (methods.isEmpty()) {
            methods.add("GET");  // Default to GET if no methods specified
        }

        // Extract parameters from the endpoint JSON
        StringBuilder parametersJson = new StringBuilder();
        JsonNode parametersNode = endpointNode.path("parameters");
        if (parametersNode.isArray() && parametersNode.size() > 0) {
            try {
                parametersJson.append(objectMapper.writeValueAsString(parametersNode));
                logger.info("Extracted {} parameters for endpoint: {}", parametersNode.size(), name);
            } catch (Exception e) {
                logger.warn("Failed to serialize parameters for endpoint {}: {}", name, e.getMessage());
            }
        }

        // Create function
        Function function = Function.builder()
                .name(name)
                .path(path)
                .methods(methods)
                .source(source)
                .appName("app")  // Default app name
                .language("java")
                .framework("spring")
                .build();

        // Add package and class info
        function.setFilePath(packageName.replace('.', '/') + "/" + className + ".java");

        // Extract dependencies
        Set<String> dependencies = new HashSet<>();
        JsonNode dependenciesNode = endpointNode.path("dependencies");
        if (dependenciesNode.isArray()) {
            dependenciesNode.forEach(dep -> dependencies.add(dep.asText()));
        }
        function.setDependencies(dependencies);

        // Extract dependency sources and store parameters information
        Map<String, String> dependencySources = new HashMap<>();
        
        // Store parameters information for later use by JavaFunctionTransformer
        if (parametersJson.length() > 0) {
            dependencySources.put("__ENDPOINT_PARAMETERS__", parametersJson.toString());
            logger.info("Extracted {} parameters for endpoint: {}", parametersNode.size(), name);
        }
        
        JsonNode serviceSourcesNode = rootNode.path("serviceSources");
        if (serviceSourcesNode.isObject()) {
            dependencies.forEach(dependency -> {
                JsonNode sourceNode = serviceSourcesNode.path(dependency);
                if (!sourceNode.isMissingNode()) {
                    dependencySources.put(dependency, sourceNode.asText());
                }
            });
        }
        function.setDependencySources(dependencySources);
        
        // Extract environment variables
        Set<String> envVars = new HashSet<>();
        JsonNode envVarsNode = rootNode.path("environmentVariables");
        if (envVarsNode.isArray()) {
            envVarsNode.forEach(env -> envVars.add(env.asText()));
        }
        function.setEnvVars(envVars);

        return function;
    }

    /**
     * Execute the Java Spring analyzer JAR.
     */
    private String executeJavaAnalyzer(String targetAppPath) throws ExternalProcessException {
        List<String> command = List.of(
                this.javaExecutable,
                "-jar",
                this.analyzerJarPath.toString(),
                targetAppPath
        );
        
        logger.info("Executing Java analyzer: {}", String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        StringBuilder output = new StringBuilder();
        StringBuilder logs = new StringBuilder();
        Process process = null;
        
        try {
            process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean jsonFound = false;
                
                while ((line = reader.readLine()) != null) {
                    // Salvăm toate liniile pentru logging și debugging
                    logs.append(line).append("\n");
                    
                    // Căutăm un început de obiect JSON
                    if (line.trim().startsWith("{")) {
                        jsonFound = true;
                        output.append(line).append("\n");
                    }
                    // Dacă am găsit deja JSON și continuăm cu linii ale obiectului
                    else if (jsonFound && (line.trim().startsWith("\"") || 
                                        line.trim().startsWith("}") || 
                                        line.trim().startsWith("[") ||
                                        line.trim().startsWith("]") ||
                                        line.contains(":"))) {
                        output.append(line).append("\n");
                    }
                }
            }
            
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ExternalProcessException("java", "Spring analyzer timed out after 120 seconds");
            }
            
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.error("Spring analyzer process failed with exit code {}. Output:\n{}", exitCode, logs.toString());
                throw new ExternalProcessException("java", "Spring analyzer failed with exit code " + exitCode);
            }
            
            // Verifică dacă avem un JSON valid
            if (output.length() == 0) {
                logger.error("Spring analyzer produced no valid JSON output. Full output was:\n{}", logs.toString());
                throw new ExternalProcessException("java", "Spring analyzer produced no valid JSON output");
            }
            
            String jsonOutput = output.toString();
            logger.debug("Extracted JSON from analyzer: {}", jsonOutput);
            
            // Validează că avem JSON valid
            try {
                objectMapper.readTree(jsonOutput);
                return jsonOutput;
            } catch (Exception e) {
                logger.error("Invalid JSON extracted from analyzer output: {}", e.getMessage());
                logger.error("Raw output was:\n{}", logs.toString());
                throw new ExternalProcessException("java", "Invalid JSON from analyzer: " + e.getMessage());
            }
            
        } catch (IOException e) {
            throw new ExternalProcessException("java", "I/O error running Spring analyzer: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw new ExternalProcessException("java", "Spring analyzer was interrupted: " + e.getMessage(), e);
        }
    }

    /**
     * Find all routes in the Spring application.
     * @return A list of Route objects.
     */
    public List<Route> findRoutes() {
        String operation = "Finding routes";
        logger.info("{} for Spring application at: {}", operation, appPath);

        String jsonResult = null;
        try {
            // 1. Execute Java analyzer JAR
            jsonResult = executeJavaAnalyzer(this.appPath);

            // 2. Parse to JsonNode first
            JsonNode rootNode = objectMapper.readTree(jsonResult);
            
            // 3. Find endpoints node
            JsonNode endpointsNode = rootNode.path("endpoints");
            if (endpointsNode.isMissingNode() || !endpointsNode.isArray()) {
                throw new CodeAnalysisException("java", "Invalid JSON structure from analyzer: 'endpoints' node missing or invalid.");
            }
            
            // 4. Convert Spring endpoints to Routes
            List<Route> routes = new ArrayList<>();
            for (JsonNode endpointNode : endpointsNode) {
                String name = endpointNode.path("name").asText();
                String path = endpointNode.path("path").asText();
                String source = endpointNode.path("source").asText();
                String className = endpointNode.path("className").asText();
                String packageName = endpointNode.path("packageName").asText();
                
                // Extract methods
                List<String> methods = new ArrayList<>();
                JsonNode methodsNode = endpointNode.path("methods");
                if (methodsNode.isArray()) {
                    methodsNode.forEach(method -> methods.add(method.asText()));
                }
                if (methods.isEmpty()) {
                    methods.add("GET");  // Default to GET if no methods specified
                }
                
                // Create route
                Route route = Route.builder()
                        .name(name)
                        .path(path)
                        .methods(methods)
                        .source(source)
                        .appName("app")  // Default app name
                        .build();
                
                // Add package and class info
                route.setFilePath(packageName.replace('.', '/') + "/" + className + ".java");
                
                // Set function name
                route.setFunctionName(name);
                
                // Set handler method and class path
                route.setHandlerMethod(name);  // The name field typically contains the method name
                route.setClassPath(packageName + "." + className);
                
                routes.add(route);
            }
            
            logger.info("Successfully found {} routes by analyzing functions.", routes.size());
            return routes;
            
        } catch (Exception e) {
            logger.error("Error finding routes: {}", e.getMessage(), e);
            throw new CodeAnalysisException("java", "Failed to find routes: " + e.getMessage(), e);
        }
    }
}