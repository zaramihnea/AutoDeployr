package com.infrastructure.services.analyzer;

import com.domain.entities.ApplicationAnalysisResult;
import com.domain.entities.Function;
import com.domain.entities.Route;
import com.domain.exceptions.BusinessRuleException;
import com.infrastructure.exceptions.CodeAnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyzer for Python Flask applications
 */
@Component
public class PythonApplicationAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(PythonApplicationAnalyzer.class);

    // Regex patterns for Python code analysis
    private static final String FLASK_APP_PATTERN = "\\s*([\\w_]+)\\s*=\\s*Flask\\(.*\\)";
    private static final String ROUTE_PATTERN = "@([\\w_]+)\\.route\\(['\"]([^'\"]+)['\"](?:,\\s*methods=\\[\\s*(.*?)\\s*\\])?.*?\\)";
    private static final String FUNCTION_PATTERN = "def\\s+([\\w_]+)\\s*\\(([^)]*)\\)\\s*:";
    private static final String IMPORT_PATTERN = "(?:from\\s+(\\S+)\\s+import\\s+([^\\n]+))|(?:import\\s+([^\\n]+))";

    /**
     * Analyze Python application
     *
     * @param appPath Application path
     * @return Analysis result
     */
    public ApplicationAnalysisResult analyze(String appPath) {
        try {
            // Initialize result with basic information
            ApplicationAnalysisResult result = new ApplicationAnalysisResult();
            result.setLanguage("python");
            result.setFramework("flask");

            // Collect Python files for analysis
            List<File> pythonFiles = findPythonFiles(appPath);
            if (pythonFiles.isEmpty()) {
                throw new BusinessRuleException("No Python files found in the application");
            }

            // Find Flask app name
            String flaskAppName = findFlaskAppName(pythonFiles);
            result.setAppName(flaskAppName);

            // Extract functions and imports
            result.setFunctions(extractFunctionsFromPythonFiles(pythonFiles, appPath));
            result.setImports(extractImportsFromPythonFiles(pythonFiles, appPath));
            
            // Find and extract routes
            List<Route> routes = findRoutes(pythonFiles, flaskAppName, appPath);
            result.setRoutes(routes);

            return result;
        } catch (BusinessRuleException e) {
            // Re-throw domain exceptions
            throw e;
        } catch (Exception e) {
            logger.error("Error analyzing Python application: {}", e.getMessage(), e);
            throw new CodeAnalysisException("python", "Failed to analyze Python application: " + e.getMessage(), e);
        }
    }

    /**
     * Extract functions from Python files
     */
    private Map<String, Map<String, String>> extractFunctionsFromPythonFiles(List<File> pythonFiles, String appPath) {
        Map<String, Map<String, String>> functions = new HashMap<>();

        for (File file : pythonFiles) {
            try {
                String content = Files.readString(file.toPath());
                Pattern pattern = Pattern.compile(FUNCTION_PATTERN);
                Matcher matcher = pattern.matcher(content);

                while (matcher.find()) {
                    String functionName = matcher.group(1);
                    int startPosition = matcher.start();
                    String functionSource = extractFunctionSource(content, startPosition);
                    
                    Map<String, String> functionInfo = new HashMap<>();
                    functionInfo.put("source", functionSource);
                    functionInfo.put("file", getRelativePath(appPath, file));
                    
                    functions.put(functionName, functionInfo);
                }
            } catch (IOException e) {
                logger.error("Error processing file {}: {}", file, e.getMessage(), e);
                throw new CodeAnalysisException("python", "Error reading Python file: " + file, e);
            }
        }

        return functions;
    }

    /**
     * Extract imports from Python files
     */
    public Map<String, List<Function.ImportDefinition>> extractImportsFromPythonFiles(
            List<File> pythonFiles, String appPath) {
        Map<String, List<Function.ImportDefinition>> imports = new HashMap<>();

        for (File file : pythonFiles) {
            try {
                String content = Files.readString(file.toPath());
                List<Function.ImportDefinition> fileImports = extractImports(content);
                imports.put(getRelativePath(appPath, file), fileImports);
            } catch (IOException e) {
                logger.error("Error extracting imports from {}: {}", file, e.getMessage(), e);
                throw new CodeAnalysisException("python", "Error reading Python file for imports: " + file, e);
            }
        }

        return imports;
    }

    /**
     * Find Python files in the application
     */
    private List<File> findPythonFiles(String appPath) {
        List<File> pythonFiles = new ArrayList<>();

        try {
            try (Stream<Path> paths = Files.walk(Path.of(appPath))) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".py"))
                        .forEach(p -> pythonFiles.add(p.toFile()));
            }
        } catch (IOException e) {
            logger.error("Error finding Python files: {}", e.getMessage(), e);
            throw new CodeAnalysisException("python", "Error finding Python files: " + e.getMessage(), e);
        }

        return pythonFiles;
    }

    /**
     * Find Flask app name in Python files
     */
    private String findFlaskAppName(List<File> pythonFiles) {
        for (File file : pythonFiles) {
            try {
                String content = Files.readString(file.toPath());
                Pattern pattern = Pattern.compile(FLASK_APP_PATTERN);
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    String appName = matcher.group(1);
                    logger.info("Found Flask application name: {}", appName);
                    return appName;
                }
            } catch (IOException e) {
                logger.error("Error reading file {}: {}", file, e.getMessage(), e);
                throw new CodeAnalysisException("python", "Error reading file: " + file, e);
            }
        }
        return "app"; // Default Flask app name
    }

    /**
     * Find routes in Python files
     */
    public List<Route> findRoutes(List<File> pythonFiles, String appName, String appPath) {
        List<Route> routes = new ArrayList<>();

        for (File file : pythonFiles) {
            try {
                String content = Files.readString(file.toPath());
                Pattern pattern = Pattern.compile(ROUTE_PATTERN);
                Matcher matcher = pattern.matcher(content);

                while (matcher.find()) {
                    String foundAppName = matcher.group(1);
                    if (foundAppName.equals(appName)) {
                        String path = matcher.group(2);
                        String methodsStr = matcher.group(3);
                        
                        logger.debug("Found route in file {} with path: {}, methods string: '{}'", 
                                file.getName(), path, methodsStr);
                                
                        List<String> methods = parseHttpMethods(methodsStr);
                        
                        // Find function name associated with this route
                        int routeEndPos = matcher.end();
                        String functionName = findFunctionNameForRoute(content, routeEndPos, appName);
                        if (functionName != null) {
                            Route route = new Route();
                            route.setName(functionName);
                            route.setPath(path);
                            route.setMethods(methods);
                            route.setFunctionName(functionName);
                            route.setAppName(appName);
                            route.setFilePath(getRelativePath(appPath, file));
                            
                            // Find function source to set as route source
                            String functionSource = extractFunctionSource(content, routeEndPos);
                            route.setSource(functionSource);
                            
                            routes.add(route);
                            
                            logger.debug("Added route: {} with methods: {} for function: {}",
                                    path, methods, functionName);
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("Error finding routes in {}: {}", file, e.getMessage(), e);
                throw new CodeAnalysisException("python", "Error finding routes: " + e.getMessage(), e);
            }
        }

        return routes;
    }

    /**
     * Parse HTTP methods string
     */
    private List<String> parseHttpMethods(String methodsStr) {
        List<String> methods = new ArrayList<>();
        if (methodsStr == null || methodsStr.isEmpty()) {
            // Default to GET if no methods specified
            methods.add("GET");
            return methods;
        }

        // Extract methods from string like: 'GET', 'POST'
        Pattern methodPattern = Pattern.compile("'([^']+)'|\"([^\"]+)\"");
        Matcher methodMatcher = methodPattern.matcher(methodsStr);
        while (methodMatcher.find()) {
            String method = methodMatcher.group(1) != null ? methodMatcher.group(1) : methodMatcher.group(2);
            methods.add(method);
        }

        // Ensure we have at least one method
        if (methods.isEmpty()) {
            methods.add("GET");
        }
        
        logger.debug("Parsed HTTP methods from '{}': {}", methodsStr, methods);
        return methods;
    }

    /**
     * Find function name associated with a route
     */
    private String findFunctionNameForRoute(String content, int startPos, String appName) {
        Pattern pattern = Pattern.compile(FUNCTION_PATTERN);
        Matcher matcher = pattern.matcher(content.substring(startPos));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extract function source code
     */
    private String extractFunctionSource(String content, int startPos) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.substring(startPos).split("\n");

        // Find base indentation from the function definition line
        int baseIndentation = 0;
        if (lines.length > 0) {
            String firstLine = lines[0];
            baseIndentation = firstLine.indexOf("def ");
        }

        boolean inFunction = true;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (i > 0) {
                // Check if we've exited the function by comparing indentation
                if (!line.isEmpty() && !line.trim().startsWith("#")) {
                    int currentIndent = line.length() - line.stripLeading().length();
                    if (currentIndent <= baseIndentation) {
                        inFunction = false;
                        break;
                    }
                }
            }

            if (inFunction) {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Extract imports from content
     */
    private List<Function.ImportDefinition> extractImports(String content) {
        List<Function.ImportDefinition> imports = new ArrayList<>();
        Pattern pattern = Pattern.compile(IMPORT_PATTERN);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String fromModule = matcher.group(1);
            String importsList = matcher.group(2);
            String directImport = matcher.group(3);

            if (fromModule != null && importsList != null) {
                // from module import name
                processFromImport(imports, fromModule, importsList);
            } else if (directImport != null) {
                // import module
                processImport(imports, directImport);
            }
        }

        return imports;
    }

    /**
     * Process "from X import Y" imports
     */
    private void processFromImport(List<Function.ImportDefinition> imports, String module, String importsStr) {
        String[] importItems = importsStr.split(",");
        for (String item : importItems) {
            item = item.trim();
            if (item.isEmpty()) continue;

            // Handle "as" alias
            String[] parts = item.split("\\s+as\\s+");
            String name = parts[0].trim();
            String alias = parts.length > 1 ? parts[1].trim() : name;

            Function.ImportDefinition importDef = new Function.ImportDefinition();
            importDef.setModule(module + "." + name);
            importDef.setAlias(alias);
            imports.add(importDef);
        }
    }

    /**
     * Process "import X" imports
     */
    private void processImport(List<Function.ImportDefinition> imports, String importsStr) {
        String[] importItems = importsStr.split(",");
        for (String item : importItems) {
            item = item.trim();
            if (item.isEmpty()) continue;

            // Handle "as" alias and module paths
            String[] parts = item.split("\\s+as\\s+");
            String modulePath = parts[0].trim();
            String alias = parts.length > 1 ? parts[1].trim() : modulePath.substring(modulePath.lastIndexOf('.') + 1);

            Function.ImportDefinition importDef = new Function.ImportDefinition();
            importDef.setModule(modulePath);
            importDef.setAlias(alias);
            imports.add(importDef);
        }
    }

    /**
     * Get relative path of a file relative to a base path
     */
    private String getRelativePath(String basePath, File file) {
        try {
            Path base = Path.of(basePath).normalize().toAbsolutePath();
            Path filePath = file.toPath().normalize().toAbsolutePath();
            return base.relativize(filePath).toString();
        } catch (Exception e) {
            logger.warn("Error getting relative path for {}: {}", file, e.getMessage());
            return file.getName();
        }
    }
} 