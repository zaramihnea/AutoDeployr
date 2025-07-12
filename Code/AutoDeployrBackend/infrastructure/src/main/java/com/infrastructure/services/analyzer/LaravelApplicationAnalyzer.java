package com.infrastructure.services.analyzer;

import com.domain.entities.ApplicationAnalysisResult;
import com.domain.entities.Function;
import com.domain.entities.Route;
import com.domain.exceptions.BusinessRuleException;
import com.infrastructure.exceptions.CodeAnalysisException;
import com.infrastructure.exceptions.ExternalProcessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.HashSet;

/**
 * Analyzer for PHP Laravel applications
 */
@Component
public class LaravelApplicationAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(LaravelApplicationAnalyzer.class);

    private final ObjectMapper objectMapper;
    private final String phpExecutable;
    private final Path analyzerScriptPath;

    public LaravelApplicationAnalyzer() {
        this.objectMapper = new ObjectMapper();
        this.phpExecutable = findPhpExecutable();
        
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        this.analyzerScriptPath = projectRoot.resolve("analyzers/laravel-analyzer/analyzer.php");

        if (!Files.exists(analyzerScriptPath)) {
            throw new CodeAnalysisException("php", "Laravel analyzer script not found at: " + analyzerScriptPath.toAbsolutePath());
        }

        logger.info("Initialized Laravel analyzer");
        logger.debug("Using PHP executable: {}", this.phpExecutable);
        logger.debug("Using Analyzer script: {}", this.analyzerScriptPath.toAbsolutePath());
    }

    /**
     * Analyze Laravel application
     *
     * @param appPath Application path
     * @return Analysis result
     */
    public ApplicationAnalysisResult analyze(String appPath) {
        try {
            logger.info("Analyzing Laravel application at path: {}", appPath);

            // Validate Laravel application
            validateLaravelApplication(appPath);

            // Execute PHP analyzer
            String jsonResult = executePhpAnalyzer(appPath);

            // Parse results
            return parseAnalyzerResult(jsonResult);

        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error analyzing Laravel application: {}", e.getMessage(), e);
            throw new CodeAnalysisException("php", "Failed to analyze Laravel application: " + e.getMessage(), e);
        }
    }

    /**
     * Find routes in Laravel application
     */
    public List<Route> findRoutes(String appPath) {
        ApplicationAnalysisResult result = analyze(appPath);
        return result.getRoutes();
    }

    /**
     * Extract functions from Laravel analysis
     */
    public List<Function> analyzeFunctions(String appPath) {
        try {
            String jsonResult = executePhpAnalyzer(appPath);
            return parseFunctionsFromJson(jsonResult);
        } catch (Exception e) {
            logger.error("Error analyzing Laravel functions: {}", e.getMessage(), e);
            throw new CodeAnalysisException("php", "Failed to analyze Laravel functions: " + e.getMessage(), e);
        }
    }

    private String findPhpExecutable() {
        String[] candidates = {"php", "php8.1", "php8.0"};
        for (String candidate : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(candidate, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    reader.lines().forEach(line -> logger.trace("{} --version output: {}", candidate, line));
                }
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    logger.debug("Found PHP executable: {}", candidate);
                    return candidate;
                }
            } catch (Exception e) {
                logger.trace("Executable '{}' not found or failed: {}", candidate, e.getMessage());
            }
        }
        logger.error("Could not find PHP executable on system PATH.");
        throw new CodeAnalysisException("php", "PHP executable not found on system PATH.");
    }

    private void validateLaravelApplication(String appPath) {
        Path appPathObj = Paths.get(appPath);
        
        if (!Files.exists(appPathObj) || !Files.isDirectory(appPathObj)) {
            throw new BusinessRuleException("Invalid Laravel application path: " + appPath);
        }
        logger.info("Validating Laravel application at: {}", appPath);
        try {
            try (Stream<Path> paths = Files.walk(appPathObj, 2)) {
                List<String> allFiles = paths
                    .filter(Files::isRegularFile)
                    .map(p -> appPathObj.relativize(p).toString())
                    .collect(java.util.stream.Collectors.toList());
                logger.info("Files found in directory (max depth 2): {}", allFiles);
            }
        } catch (IOException e) {
            logger.warn("Could not list files for debugging: {}", e.getMessage());
        }
        boolean hasComposerJson = Files.exists(appPathObj.resolve("composer.json"));
        boolean hasArtisan = Files.exists(appPathObj.resolve("artisan"));
        boolean hasAppDirectory = Files.exists(appPathObj.resolve("app"));
        boolean hasRoutes = Files.exists(appPathObj.resolve("routes"));
        boolean hasControllers = Files.exists(appPathObj.resolve("app/Http/Controllers")) || 
                                  Files.exists(appPathObj.resolve("Http/Controllers"));
        boolean hasBootstrap = Files.exists(appPathObj.resolve("bootstrap"));

        boolean hasPhpFiles = false;
        boolean hasLaravelPatterns = false;
        try {
            List<File> phpFiles = findPhpFiles(appPath);
            hasPhpFiles = !phpFiles.isEmpty();
            logger.info("Found {} PHP files", phpFiles.size());
            for (File phpFile : phpFiles) {
                try {
                    String content = Files.readString(phpFile.toPath());
                    if (content.contains("Route::") || content.contains("Illuminate\\") || 
                        content.contains("use Illuminate") || content.contains("Laravel") ||
                        content.contains("namespace App\\") || content.contains("Controller") ||
                        content.contains("response()->json")) {
                        hasLaravelPatterns = true;
                        logger.info("Found Laravel patterns in file: {}", phpFile.getName());
                        break;
                    }
                } catch (IOException e) {
                    // Continue checking other files
                }
            }
        } catch (Exception e) {
            logger.debug("Error checking PHP files for Laravel patterns: {}", e.getMessage());
        }
        boolean isLaravel = (hasComposerJson && hasArtisan) ||
                           (hasArtisan && hasAppDirectory) ||
                           (hasRoutes && hasControllers) ||
                           (hasBootstrap && hasAppDirectory) ||
                           (hasPhpFiles && hasLaravelPatterns) ||
                           (hasControllers && hasLaravelPatterns);

        logger.info("Laravel validation results - composer.json: {}, artisan: {}, app/: {}, routes/: {}, controllers: {}, bootstrap/: {}, phpFiles: {}, laravelPatterns: {}, isLaravel: {}",
                   hasComposerJson, hasArtisan, hasAppDirectory, hasRoutes, hasControllers, hasBootstrap, hasPhpFiles, hasLaravelPatterns, isLaravel);

        if (!isLaravel) {
            logger.warn("Directory validation failed for Laravel application: {}. " +
                       "composer.json: {}, artisan: {}, app/: {}, routes/: {}, controllers: {}, bootstrap/: {}, phpFiles: {}, laravelPatterns: {}",
                       appPath, hasComposerJson, hasArtisan, hasAppDirectory, hasRoutes, hasControllers, hasBootstrap, hasPhpFiles, hasLaravelPatterns);
            throw new BusinessRuleException("Directory does not appear to be a Laravel application: " + appPath + 
                                           ". Please ensure it contains Laravel-specific files or patterns.");
        }

        logger.info("Successfully validated Laravel application structure at: {}", appPath);
    }

    private String executePhpAnalyzer(String targetAppPath) throws ExternalProcessException {
        List<String> command = List.of(
                this.phpExecutable,
                this.analyzerScriptPath.toString(),
                "--app-path",
                targetAppPath
        );

        String commandString = String.join(" ", command);
        logger.info("Executing Laravel analyzer command: {}", commandString);

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            
            // Set working directory to analyzer directory for dependencies
            processBuilder.directory(analyzerScriptPath.getParent().toFile());
            
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
                logger.error("Laravel analyzer process timed out after {} seconds.", timeoutSeconds);
                process.destroyForcibly();
                throw new ExternalProcessException("laravel analyzer", "Process timed out after " + timeoutSeconds + " seconds.");
            }

            int exitCode = process.exitValue();
            String resultOutput = output.toString();

            if (exitCode != 0) {
                logger.error("Laravel analyzer script failed with exit code: {}. Command: {}", exitCode, commandString);
                logger.error("Analyzer Output:\n{}", resultOutput);
                throw new ExternalProcessException("laravel analyzer", "Script failed with exit code " + exitCode + ". Check logs for analyzer output.");
            }

            logger.info("Laravel analyzer executed successfully.");
            logger.debug("Raw analyzer output:\n{}", resultOutput);

            return resultOutput.trim();

        } catch (IOException | InterruptedException e) {
            logger.error("Error executing Laravel analyzer: {}", e.getMessage(), e);
            throw new ExternalProcessException("laravel analyzer", "Failed to execute analyzer: " + e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private ApplicationAnalysisResult parseAnalyzerResult(String jsonResult) {
        try {
            // Clean the result
            String cleanedJson = extractJsonFromOutput(jsonResult);
            JsonNode rootNode = objectMapper.readTree(cleanedJson);
            
            ApplicationAnalysisResult result = new ApplicationAnalysisResult();
            result.setLanguage("php");
            result.setFramework("laravel");
            result.setAppName(rootNode.path("app_name").asText("Laravel"));

            // Parse functions
            JsonNode functionsNode = rootNode.path("functions");
            if (functionsNode.isArray()) {
                List<Route> routes = new ArrayList<>();
                Map<String, Map<String, String>> functions = new HashMap<>();

                for (JsonNode functionNode : functionsNode) {
                    String name = functionNode.path("name").asText();
                    String path = functionNode.path("path").asText();
                    String source = functionNode.path("source").asText();
                    String controller = functionNode.path("controller").asText();

                    // Create route
                    Route route = new Route();
                    route.setPath(path);
                    route.setMethods(parseStringArray(functionNode.path("methods")));
                    route.setFunctionName(name);
                    route.setSource(source);
                    routes.add(route);

                    // Create function entry
                    Map<String, String> functionInfo = new HashMap<>();
                    functionInfo.put("source", source);
                    functionInfo.put("file", functionNode.path("file_path").asText());
                    functionInfo.put("controller", controller);
                    functions.put(name, functionInfo);
                }

                result.setRoutes(routes);
                result.setFunctions(functions);
            }

            return result;

        } catch (Exception e) {
            logger.error("Error parsing analyzer result: {}", e.getMessage(), e);
            throw new CodeAnalysisException("php", "Failed to parse analyzer result: " + e.getMessage(), e);
        }
    }

    private List<Function> parseFunctionsFromJson(String jsonResult) {
        try {
            // Clean the result
            String cleanedJson = extractJsonFromOutput(jsonResult);
            JsonNode rootNode = objectMapper.readTree(cleanedJson);
            List<Function> functions = new ArrayList<>();

            JsonNode functionsNode = rootNode.path("functions");
            if (functionsNode.isArray()) {
                for (JsonNode functionNode : functionsNode) {
                    Function function = new Function();
                    function.setName(functionNode.path("name").asText());
                    function.setPath(functionNode.path("path").asText());
                    function.setMethods(parseStringArray(functionNode.path("methods")));
                    function.setSource(functionNode.path("source").asText());
                    function.setLanguage("php");
                    function.setFramework("laravel");
                    function.setAppName(functionNode.path("app_name").asText("Laravel"));
                    function.setRequiresDb(functionNode.path("requires_db").asBoolean(false));

                    // Parse imports
                    JsonNode importsNode = functionNode.path("imports");
                    if (importsNode.isArray()) {
                        List<Function.ImportDefinition> imports = new ArrayList<>();
                        for (JsonNode importNode : importsNode) {
                            Function.ImportDefinition importDef = new Function.ImportDefinition(
                                    importNode.path("namespace").asText(),
                                    importNode.path("alias").asText()
                            );
                            imports.add(importDef);
                        }
                        function.setImports(imports);
                    }

                    // Parse environment variables
                    function.setEnvVars(new HashSet<>(parseStringArray(functionNode.path("env_vars"))));

                    functions.add(function);
                }
            }

            return functions;

        } catch (Exception e) {
            logger.error("Error parsing functions from JSON: {}", e.getMessage(), e);
            throw new CodeAnalysisException("php", "Failed to parse functions: " + e.getMessage(), e);
        }
    }

    /**
     * Extract JSON from analyzer output by removing any debug messages before the JSON
     */
    private String extractJsonFromOutput(String output) {
        if (output == null || output.trim().isEmpty()) {
            return "{}";
        }
        int jsonStart = output.indexOf('{');
        if (jsonStart == -1) {
            logger.warn("No JSON found in analyzer output: {}", output);
            return "{}";
        }
        String cleanedJson = output.substring(jsonStart).trim();
        if (jsonStart > 0) {
            String strippedContent = output.substring(0, jsonStart).trim();
            logger.info("Stripped debug output from analyzer result: '{}'", strippedContent);
        }
        if (!cleanedJson.startsWith("{") || !cleanedJson.contains("}")) {
            logger.warn("Extracted JSON may be malformed: {}", cleanedJson);
        }

        return cleanedJson;
    }

    private List<String> parseStringArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /**
     * Find PHP files in the application
     */
    private List<File> findPhpFiles(String appPath) {
        List<File> phpFiles = new ArrayList<>();

        try {
            try (Stream<Path> paths = Files.walk(Path.of(appPath))) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".php"))
                        .filter(p -> !p.toString().contains("vendor/")) // Exclude vendor files
                        .forEach(p -> phpFiles.add(p.toFile()));
            }
        } catch (IOException e) {
            logger.error("Error finding PHP files: {}", e.getMessage(), e);
            throw new CodeAnalysisException("php", "Error finding PHP files: " + e.getMessage(), e);
        }

        return phpFiles;
    }
}