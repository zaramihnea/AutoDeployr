package com.infrastructure.services.analyzer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.domain.entities.Function;
import com.domain.entities.Route;
import com.domain.exceptions.ValidationException;
import com.infrastructure.exceptions.CodeAnalysisException;
import com.infrastructure.exceptions.ExternalProcessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

/**
 * Analyzer for Python Flask applications using Python AST.
 * Includes detailed logging for parsing function analysis results.
 */
public class PythonCodeAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(PythonCodeAnalyzer.class);

    private final String appPath;
    private final String pythonExecutable;
    private final Path analyzerScriptPath;
    private final ObjectMapper objectMapper;

    /**
     * Create a new Python code analyzer.
     * @param appPath Path to the application directory to be analyzed.
     */
    public PythonCodeAnalyzer(String appPath) {
        if (appPath == null || appPath.isBlank()) {
            throw new ValidationException("appPath", "Application path cannot be empty.");
        }
        Path pathObj = Paths.get(appPath);
        if (!Files.isDirectory(pathObj)) {
            throw new ValidationException("appPath", "Application path does not exist or is not a directory: " + appPath);
        }
        this.appPath = appPath;

        this.pythonExecutable = findPythonExecutable();
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        this.analyzerScriptPath = projectRoot.resolve("analyzers/flask-ast-analyzer/analyzer.py");

        if (!Files.exists(analyzerScriptPath)) {
            throw new CodeAnalysisException("python", "Analyzer script not found at resolved path: " + analyzerScriptPath.toAbsolutePath());
        }

        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        logger.info("Initialized Python Flask analyzer for app path: {}", appPath);
        logger.debug("Using Python executable: {}", this.pythonExecutable);
        logger.debug("Using Analyzer script: {}", this.analyzerScriptPath.toAbsolutePath());
    }

    private String findPythonExecutable() {
        String[] candidates = {"python3", "python"};
        for (String candidate : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    reader.lines().forEach(line -> logger.trace("{} --version output: {}", candidate, line));
                }
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    logger.debug("Found Python executable: {}", candidate);
                    return candidate;
                }
            } catch (Exception e) {
                logger.trace("Executable '{}' not found or failed: {}", candidate, e.getMessage());
            }
        }
        logger.error("Could not find 'python3' or 'python' executable on system PATH.");
        throw new CodeAnalysisException("python", "Python executable (python3 or python) not found on system PATH.");
    }

    /**
     * Analyzes the Flask application and parses the results, with detailed debugging logs.
     * @return A List of Function objects.
     */
    public List<Function> analyze() {
        String operation = "Flask application analysis";
        logger.info("Starting {} for: {}", operation, appPath);

        String jsonResult = null;
        try {
            // 1. Execute Python analyzer script
            jsonResult = executePythonAnalyzer(this.appPath);

            // 2. *** Extract JSON from the output (skip logging messages) ***
            String cleanJsonResult = extractJsonFromOutput(jsonResult);
            
            // Log the Raw JSON Output for debugging
            logger.info("------------------------------------------------------");
            logger.info("--- EXTRACTED JSON FROM analyzer.py ---");
            logger.info("{}", cleanJsonResult); // Log the clean JSON
            logger.info("--- END JSON ---");
            logger.info("------------------------------------------------------");

            // 3. *** Parse to generic JsonNode First ***
            JsonNode rootNode;
            try {
                rootNode = objectMapper.readTree(cleanJsonResult);
                logger.info("STEP 1: Successfully parsed clean JSON into Jackson JsonNode tree.");
            } catch(Exception e) {
                logger.error("FATAL: Failed to parse clean JSON string into JsonNode tree: {}", e.getMessage(), e);
                logger.error("Raw analyzer output causing error:\n{}", jsonResult);
                throw new CodeAnalysisException("python", "Failed basic JSON parsing: " + e.getMessage(), e);
            }

            // 4. *** Explicitly Find and Inspect 'functions' and 'dependency_sources' ***
            JsonNode functionsNode = rootNode.path("functions"); // Use path() for safety
            if (functionsNode.isMissingNode() || !functionsNode.isArray()) {
                logger.error("FATAL: 'functions' node is MISSING or NOT AN ARRAY in the parsed JsonNode.");
                logger.error("Parsed Root Node Structure:\n{}", rootNode.toPrettyString()); // Log structure on error
                throw new CodeAnalysisException("python", "Invalid JSON structure from analyzer: 'functions' node missing or invalid.");
            } else {
                logger.info("STEP 2: 'functions' node found in JsonNode and is an array. Size: {}", functionsNode.size());
                // Log the *first* function's dependency_sources node specifically if functions exist
                if (functionsNode.size() > 0) {
                    JsonNode firstFuncNode = functionsNode.get(0);
                    if (firstFuncNode == null || !firstFuncNode.isObject()) {
                        logger.warn("First element in 'functions' array is null or not an object.");
                    } else {
                        JsonNode depSourcesNode = firstFuncNode.path("dependency_sources"); // Use path()
                        if (depSourcesNode.isMissingNode()) {
                            logger.warn("!!! JsonNode Check: 'dependency_sources' node is MISSING in the first function's JsonNode !!!");
                            logger.warn("First function JsonNode content:\n{}", firstFuncNode.toPrettyString()); // Log node content if missing
                        } else if (!depSourcesNode.isObject()) {
                            logger.warn("!!! JsonNode Check: 'dependency_sources' node is NOT AN OBJECT in the first function's JsonNode. Type: {} !!!", depSourcesNode.getNodeType());
                            logger.warn("Value of 'dependency_sources' node: {}", depSourcesNode.toString());
                        } else {
                            // Log the keys/content Jackson sees BEFORE mapping to the Java Map
                            logger.info(">>> JsonNode Check: 'dependency_sources' node in first function (as text): {}", depSourcesNode.toString());
                        }
                        // Log the dependencies array node too for comparison
                        JsonNode depsNode = firstFuncNode.path("dependencies");
                        logger.info(">>> JsonNode Check: 'dependencies' node in first function (as text): {}", depsNode.toString());
                    }

                } else {
                    logger.info("STEP 2: 'functions' array node is empty.");
                }
            }

            // 5. *** Attempt Deserialization to List<Function> ***
            List<Function> functions;
            logger.info("STEP 3: Attempting to deserialize 'functions' JsonNode into List<Function> using TypeReference...");
            try {
                functions = objectMapper.readValue(
                        functionsNode.traverse(), // Use traverse() on the array node
                        new TypeReference<List<Function>>() {}
                );
                logger.info("STEP 3: Successfully deserialized JsonNode into List<Function>. Count: {}", functions.size());
            } catch (Exception e) {
                logger.error("FATAL: Failed during Jackson deserialization from 'functions' JsonNode into List<Function>: {}", e.getMessage(), e);
                logger.error("Functions JsonNode snippet causing error:\n{}", functionsNode.toPrettyString().substring(0, Math.min(functionsNode.toPrettyString().length(), 1000))); // Log snippet
                // You might need to check your Function class fields, annotations (@JsonProperty), and nested classes (ImportDefinition)
                throw new CodeAnalysisException("python", "Failed Jackson mapping to List<Function>: " + e.getMessage(), e);
            }

            // 6. *** Log the Parsed Function Objects AGAIN, focusing on the map ***
            logger.info("STEP 4: Verifying content of parsed Function objects...");
            if (!functions.isEmpty()) {
                // Log details for the first function
                Function firstFunc = functions.get(0);
                String funcName = firstFunc.getName() != null ? firstFunc.getName() : "[UNKNOWN NAME]";
                logger.info("------------------------------------------------------");
                logger.info("--- Verification for First Parsed Function ('{}') ---", funcName);
                logger.info("Name: {}", firstFunc.getName());
                logger.info("Path: {}", firstFunc.getPath());
                logger.info("AppName: {}", firstFunc.getAppName());
                logger.info("Methods: {}", firstFunc.getMethods());
                logger.info("Dependencies (Set): {}", firstFunc.getDependencies()); // Should be populated
                logger.info(">>> DependencySources (Map): {}", // <<< THE KEY CHECK!
                        firstFunc.getDependencySources() == null ? "NULL" : firstFunc.getDependencySources());
                logger.info("Imports (List): {}", firstFunc.getImports()); // Check if List<ImportDefinition> is parsed
                logger.info("EnvVars (Set): {}", firstFunc.getEnvVars());
                logger.info("Language: {}", firstFunc.getLanguage());
                logger.info("Framework: {}", firstFunc.getFramework());
                logger.info("------------------------------------------------------");

                // Optional: Log all functions if debugging requires it (can be verbose)
                // for(int i = 0; i < functions.size(); i++) {
                //     Function func = functions.get(i);
                //     logger.debug("Parsed Func [{}]: Name={}, DepSources={}", i, func.getName(), func.getDependencySources());
                // }

            } else {
                logger.info("STEP 4: Parsed functions list is empty.");
            }

            // 7. Optional: Post-processing (like setting AppName from root)
            String rootAppName = rootNode.path("app_name").asText(null);
            if (rootAppName != null) {
                for(Function f : functions) {
                    if (f.getAppName() == null || f.getAppName().equals("app")) {
                        f.setAppName(rootAppName);
                    }
                    if (f.getLanguage() == null) f.setLanguage("python");
                    if (f.getFramework() == null) f.setFramework("flask");
                }
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
            // Log the raw JSON if available and an unexpected error occurred during parsing/processing
            if (jsonResult != null) {
                logger.error("Raw JSON at time of unexpected error:\n{}", jsonResult);
            }
            throw new CodeAnalysisException("python", "Unexpected failure during " + operation + ": " + e.getMessage(), e);
        }
    }

    // --- executePythonAnalyzer method remains the same ---
    private String executePythonAnalyzer(String targetAppPath) throws ExternalProcessException {
        List<String> command = List.of(
                this.pythonExecutable,
                this.analyzerScriptPath.toString(),
                "--app-path",
                targetAppPath
        );
        String commandString = String.join(" ", command);
        logger.info("Executing Python analyzer command: {}", commandString);
        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            long timeoutSeconds = 60;
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                logger.error("Python analyzer process timed out after {} seconds.", timeoutSeconds);
                process.destroyForcibly();
                throw new ExternalProcessException("python analyzer", "Process timed out after " + timeoutSeconds + " seconds.");
            }
            int exitCode = process.exitValue();
            String resultOutput = output.toString();
            if (exitCode != 0) {
                logger.error("Python analyzer script failed with exit code: {}. Command: {}", exitCode, commandString);
                logger.error("Analyzer Output:\n{}", resultOutput);
                throw new ExternalProcessException("python analyzer", "Script failed with exit code " + exitCode + ". Check logs for analyzer output.");
            }
            logger.info("Python analyzer executed successfully.");
            if (resultOutput.length() > 1000 && !logger.isTraceEnabled()) {
                logger.debug("Analyzer output received ({} bytes). Enable TRACE level for full output.", resultOutput.length());
            } else {
                logger.trace("Analyzer Output:\n{}", resultOutput);
            }
            return resultOutput;
        } catch (IOException e) {
            logger.error("I/O error executing or reading output from Python analyzer: {}", e.getMessage(), e);
            throw new ExternalProcessException("python analyzer", "I/O error during execution: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            logger.warn("Python analyzer execution was interrupted.", e);
            Thread.currentThread().interrupt();
            throw new ExternalProcessException("python analyzer", "Execution interrupted.", e);
        } finally {
            if (process != null && process.isAlive()) {
                logger.warn("Analyzer process still alive after completion/error handling, attempting to destroy forcibly.");
                process.destroyForcibly();
            }
        }
    }

    /**
     * Extract JSON content from the Python analyzer output, skipping logging messages
     * @param output Raw output from Python analyzer containing logging + JSON
     * @return Clean JSON string
     */
    private String extractJsonFromOutput(String output) {
        if (output == null || output.isEmpty()) {
            throw new CodeAnalysisException("python", "Empty output from Python analyzer");
        }

        // Look for the actual JSON object by finding a line that starts with '{'
        // This skips log messages which have timestamps at the beginning
        String[] lines = output.split("\n");
        int jsonStartLine = -1;
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("{")) {
                jsonStartLine = i;
                break;
            }
        }
        
        if (jsonStartLine == -1) {
            logger.error("No JSON object found in Python analyzer output. Output:\n{}", output);
            throw new CodeAnalysisException("python", "No JSON object found in analyzer output");
        }

        // Reconstruct the JSON from the starting line onwards
        StringBuilder jsonBuilder = new StringBuilder();
        for (int i = jsonStartLine; i < lines.length; i++) {
            jsonBuilder.append(lines[i]);
            if (i < lines.length - 1) {
                jsonBuilder.append("\n");
            }
        }
        
        String jsonContent = jsonBuilder.toString().trim();
        logger.debug("Extracted JSON content ({} chars) starting from line {} of analyzer output", 
                    jsonContent.length(), jsonStartLine);
        
        return jsonContent;
    }



    // --- findRoutes method can remain, using the updated analyze() ---
    /**
     * Finds all routes by running the analysis and mapping Function objects to Route objects.
     * @return List of Route objects.
     */
    public List<Route> findRoutes() {
        logger.info("Finding routes for Flask application at: {}", appPath);
        try {
            List<Function> functions = this.analyze(); // Calls the updated analyze()
            List<Route> routes = new ArrayList<>();
            for (Function func : functions) {
                Route route = new Route();
                route.setName(func.getName());
                route.setPath(func.getPath());
                route.setMethods(func.getMethods() != null ? new ArrayList<>(func.getMethods()) : new ArrayList<>(List.of("GET")));
                route.setAppName(func.getAppName());
                route.setSource(func.getSource());
                // Add mapping for other Route fields from Function if needed
                routes.add(route);
            }
            logger.info("Successfully found {} routes by analyzing functions.", routes.size());
            return routes;
        } catch (Exception e) {
            logger.error("Failed to find routes for application '{}': {}", appPath, e.getMessage(), e);
            if (e instanceof CodeAnalysisException || e instanceof ExternalProcessException) {
                throw (RuntimeException) e;
            }
            throw new CodeAnalysisException("python", "Failed to find routes: " + e.getMessage(), e);
        }
    }
}