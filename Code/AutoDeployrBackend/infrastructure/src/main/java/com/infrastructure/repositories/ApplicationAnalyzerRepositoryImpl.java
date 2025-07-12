package com.infrastructure.repositories;

import com.domain.entities.ApplicationAnalysisResult;
import com.domain.entities.Function;
import com.domain.entities.Route;
import com.domain.exceptions.BusinessRuleException;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.repositories.IApplicationAnalyzerRepository;
import com.infrastructure.exceptions.CodeAnalysisException;
import com.infrastructure.services.analyzer.CSharpApplicationAnalyzer;
import com.infrastructure.services.analyzer.JavaApplicationAnalyzer;
import com.infrastructure.services.analyzer.JavaCodeAnalyzer;
import com.infrastructure.services.analyzer.LaravelApplicationAnalyzer;
import com.infrastructure.services.analyzer.PythonApplicationAnalyzer;
import com.infrastructure.services.analyzer.PythonCodeAnalyzer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the application analyzer repository
 * Orchestrates analysis of various application types by delegating to specialized analyzers
 */
@Repository
@RequiredArgsConstructor
public class ApplicationAnalyzerRepositoryImpl implements IApplicationAnalyzerRepository {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationAnalyzerRepositoryImpl.class);

    private final PythonApplicationAnalyzer pythonApplicationAnalyzer;
    private final JavaApplicationAnalyzer javaApplicationAnalyzer;
    private final CSharpApplicationAnalyzer csharpApplicationAnalyzer;
    private final LaravelApplicationAnalyzer laravelApplicationAnalyzer;

    @Override
    public String detectLanguage(String appPath) {
        validateAppPath(appPath);

        try {
            try (Stream<Path> paths = Files.walk(Path.of(appPath))) {
                Map<String, Long> extensionCounts = paths
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        String fileName = path.getFileName().toString();
                        int lastDot = fileName.lastIndexOf('.');
                        return lastDot > 0 ? fileName.substring(lastDot) : "";
                    })
                    .filter(ext -> !ext.isEmpty())
                    .collect(Collectors.groupingBy(ext -> ext, Collectors.counting()));

                long pyCount = extensionCounts.getOrDefault(".py", 0L);
                long javaCount = extensionCounts.getOrDefault(".java", 0L);
                long csCount = extensionCounts.getOrDefault(".cs", 0L);
                long csprojCount = extensionCounts.getOrDefault(".csproj", 0L);
                long phpCount = extensionCounts.getOrDefault(".php", 0L);

                boolean hasComposerJson = extensionCounts.containsKey(".json") && 
                    Files.exists(Path.of(appPath, "composer.json"));
                boolean hasArtisan = Files.exists(Path.of(appPath, "artisan"));

                if (csCount > 0 || csprojCount > 0) {
                    logger.info("Detected C# as the language ({} CS files, {} csproj files)", csCount, csprojCount);
                    return "csharp";
                } else if (phpCount > 0 || hasComposerJson || hasArtisan) {
                    logger.info("Detected PHP as the language ({} PHP files, composer.json: {}, artisan: {})", 
                               phpCount, hasComposerJson, hasArtisan);
                    return "php";
                } else if (pyCount > javaCount) {
                    logger.info("Detected Python as the dominant language ({} Python files)", pyCount);
                    return "python";
                } else if (javaCount > 0) {
                    logger.info("Detected Java as the dominant language ({} Java files)", javaCount);
                    return "java";
                }
            }
        } catch (IOException e) {
            logger.error("Error detecting language: {}", e.getMessage(), e);
            throw new CodeAnalysisException("Failed to detect programming language: " + e.getMessage(), e);
        }

        throw new BusinessRuleException("Could not detect programming language in: " + appPath);
    }

    @Override
    public ApplicationAnalysisResult analyzeApplication(String appPath) {
        validateAppPath(appPath);

        String language = detectLanguage(appPath);

        if ("python".equals(language)) {
            PythonCodeAnalyzer pythonCodeAnalyzer = new PythonCodeAnalyzer(appPath);
            List<Function> functions = pythonCodeAnalyzer.analyze();
            ApplicationAnalysisResult result = new ApplicationAnalysisResult();
            result.setLanguage("python");
            result.setFramework("flask");

            if (!functions.isEmpty() && functions.get(0).getAppName() != null) {
                result.setAppName(functions.get(0).getAppName());
            } else {
                result.setAppName("app");
            }

            List<Route> routes = new ArrayList<>();
            for (Function func : functions) {
                Route route = new Route();
                route.setName(func.getName());
                route.setFunctionName(func.getName());
                route.setPath(func.getPath());
                route.setMethods(func.getMethods() != null ? new ArrayList<>(func.getMethods()) : new ArrayList<>(List.of("GET")));
                route.setAppName(func.getAppName());
                route.setSource(func.getSource());
                routes.add(route);
            }
            result.setRoutes(routes);
            Map<String, Map<String, String>> functionsMap = new HashMap<>();
            for (Function func : functions) {
                Map<String, String> funcInfo = new HashMap<>();
                funcInfo.put("source", func.getSource());
                funcInfo.put("file", func.getFilePath());
                functionsMap.put(func.getName(), funcInfo);
            }
            result.setFunctions(functionsMap);
            
            logger.info("Successfully analyzed Python application with {} functions using new analyzer", functions.size());
            return result;
            
        } else if ("java".equals(language)) {
            return javaApplicationAnalyzer.analyze(appPath);
        } else if ("csharp".equals(language)) {
            return csharpApplicationAnalyzer.analyze(appPath);
        } else if ("php".equals(language)) {
            return laravelApplicationAnalyzer.analyze(appPath);
        }

        throw new CodeAnalysisException("Unsupported language: " + language);
    }

    @Override
    public List<Route> findRoutes(String appPath) {
        validateAppPath(appPath);
        
        String language = detectLanguage(appPath);

        if ("python".equals(language)) {
            List<File> pythonFiles = findPythonFiles(appPath);
            if (pythonFiles.isEmpty()) {
                throw new BusinessRuleException("No Python files found in the application");
            }
            String flaskAppName = findFlaskAppName(pythonFiles);
            return pythonApplicationAnalyzer.findRoutes(pythonFiles, flaskAppName, appPath);
        } else if ("java".equals(language)) {
            JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer(appPath);
            return analyzer.findRoutes();
        } else if ("csharp".equals(language)) {
            return csharpApplicationAnalyzer.findRoutes(appPath);
        } else if ("php".equals(language)) {
            return laravelApplicationAnalyzer.findRoutes(appPath);
        }
        
        throw new CodeAnalysisException("Unsupported language for route extraction: " + language);
    }
    
    @Override
    public List<Function> extractFunctions(ApplicationAnalysisResult analysisResult) {
        if (analysisResult == null) {
            throw new BusinessRuleException("Analysis result cannot be null");
        }
        
        List<Function> functions = new ArrayList<>();
        if (analysisResult.getRoutes() != null) {
            for (Route route : analysisResult.getRoutes()) {
                Function function = createFunctionFromRoute(route, analysisResult);
                if (function != null) {
                    functions.add(function);
                }
            }
        }
        
        logger.info("Extracted {} functions from analysis result", functions.size());
        return functions;
    }
    
    @Override
    public List<Function> extractPythonFunctions(String appPath) {
        validateAppPath(appPath);
        
        logger.info("Extracting Python functions directly using advanced analyzer for: {}", appPath);
        
        try {
            PythonCodeAnalyzer pythonCodeAnalyzer = new PythonCodeAnalyzer(appPath);
            List<Function> functions = pythonCodeAnalyzer.analyze();
            
            logger.info("Successfully extracted {} Python functions with proper analysis", functions.size());
            return functions;
            
        } catch (Exception e) {
            logger.error("Failed to extract Python functions using advanced analyzer: {}", e.getMessage(), e);
            throw new CodeAnalysisException("python", "Failed to extract Python functions: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Function> extractJavaFunctions(String appPath) {
        validateAppPath(appPath);
        
        logger.info("Extracting Java functions directly using advanced analyzer for: {}", appPath);
        
        try {
            JavaCodeAnalyzer javaCodeAnalyzer = new JavaCodeAnalyzer(appPath);
            List<Function> functions = javaCodeAnalyzer.analyze();
            
            logger.info("Successfully extracted {} Java functions with proper analysis", functions.size());
            return functions;
            
        } catch (Exception e) {
            logger.error("Failed to extract Java functions using advanced analyzer: {}", e.getMessage(), e);
            throw new CodeAnalysisException("java", "Failed to extract Java functions: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Function> extractLaravelFunctions(String appPath) {
        validateAppPath(appPath);
        
        logger.info("Extracting Laravel functions directly using advanced analyzer for: {}", appPath);
        
        try {
            List<Function> functions = laravelApplicationAnalyzer.analyzeFunctions(appPath);
            
            logger.info("Successfully extracted {} Laravel functions with proper analysis", functions.size());
            return functions;
            
        } catch (Exception e) {
            logger.error("Failed to extract Laravel functions using advanced analyzer: {}", e.getMessage(), e);
            throw new CodeAnalysisException("php", "Failed to extract Laravel functions: " + e.getMessage(), e);
        }
    }

    /**
     * Create a Function entity from a route
     */
    private Function createFunctionFromRoute(Route route, ApplicationAnalysisResult analysisResult) {
        String functionName = route.getFunctionName();
        
        if (functionName == null || functionName.isEmpty()) {
            logger.warn("Route {} has no associated function name", route.getPath());
            if ("java".equals(analysisResult.getLanguage()) && "spring".equals(analysisResult.getFramework())) {
                if (route.getPath() != null && route.getHandlerMethod() != null) {
                    functionName = route.getHandlerMethod();
                    logger.info("Using handler method name '{}' as function name for Spring endpoint: {}", 
                               functionName, route.getPath());
                } else if (route.getPath() != null) {
                    functionName = route.getPath().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                    if (functionName.isEmpty()) {
                        functionName = "endpoint" + Math.abs(route.getPath().hashCode());
                    }
                    logger.info("Generated function name '{}' from path for Spring endpoint: {}", 
                               functionName, route.getPath());
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        Map<String, Map<String, String>> functionsMap = analysisResult.getFunctions();
        if ("java".equals(analysisResult.getLanguage()) && "spring".equals(analysisResult.getFramework()) &&
            (functionsMap == null || !functionsMap.containsKey(functionName))) {
            Function function = new Function();
            function.setName(functionName);
            function.setSource(route.getSource());
            function.setFilePath(route.getClassPath());
            function.setPath(route.getPath());
            function.setRoute(route.getPath());
            function.setMethods(route.getMethods());
            function.setHttpMethods(route.getMethods());
            function.setDependencies(new HashSet<>());
            function.setDependencySources(new HashMap<>());
            function.setEnvVars(analysisResult.getEnvVars());
            function.setLanguage(analysisResult.getLanguage());
            function.setFramework(analysisResult.getFramework());
            function.setAppName(analysisResult.getAppName());
            List<Function.ImportDefinition> functionImports = extractImportsForFunction(route.getClassPath(), analysisResult);
            function.setImports(functionImports);
            
            logger.info("Created Spring function {} with route {} and methods {}", 
                    functionName, route.getPath(), route.getMethods());
            
            return function;
        }
        
        if (functionsMap == null || !functionsMap.containsKey(functionName)) {
            logger.warn("Function '{}' source not found in analysis results", functionName);
            return null;
        }
        
        Map<String, String> funcInfo = functionsMap.get(functionName);
        String source = funcInfo.get("source");
        String filePath = funcInfo.get("file");
        Set<String> dependencies = findFunctionDependencies(functionName, source, analysisResult);
        Map<String, String> dependencySources = createDependencySourcesMap(dependencies, analysisResult);
        Function function = new Function();
        function.setName(functionName);
        function.setSource(source);
        function.setFilePath(filePath);
        function.setPath(route.getPath());
        function.setRoute(route.getPath());
        function.setMethods(route.getMethods());
        function.setHttpMethods(route.getMethods());
        function.setDependencies(dependencies);
        function.setDependencySources(dependencySources);
        function.setEnvVars(analysisResult.getEnvVars());
        function.setLanguage(analysisResult.getLanguage());
        function.setFramework(analysisResult.getFramework());
        function.setAppName(analysisResult.getAppName());
        List<Function.ImportDefinition> functionImports = extractImportsForFunction(filePath, analysisResult);
        function.setImports(functionImports);
        
        logger.debug("Created function {} with route {} and methods {}", 
                functionName, route.getPath(), route.getMethods());
        
        return function;
    }
    
    /**
     * Create a map of dependency sources
     */
    private Map<String, String> createDependencySourcesMap(
            Set<String> dependencies, ApplicationAnalysisResult analysisResult) {
        Map<String, String> dependencySources = new HashMap<>();
        Map<String, Map<String, String>> functionsMap = analysisResult.getFunctions();
        
        if (functionsMap != null) {
            for (String dependency : dependencies) {
                if (functionsMap.containsKey(dependency)) {
                    Map<String, String> depInfo = functionsMap.get(dependency);
                    dependencySources.put(dependency, depInfo.get("source"));
                }
            }
        }
        
        return dependencySources;
    }
    
    /**
     * Find function dependencies by analyzing function source
     */
    private Set<String> findFunctionDependencies(String functionName, String source, ApplicationAnalysisResult analysisResult) {
        Set<String> dependencies = new HashSet<>();
        Map<String, Map<String, String>> functionsMap = analysisResult.getFunctions();
        
        if (functionsMap == null || source == null) {
            return dependencies;
        }
        for (String potentialDependency : functionsMap.keySet()) {
            if (potentialDependency.equals(functionName)) {
                continue;
            }
            if (source.contains(potentialDependency + "(") && 
                    isRelevantFunctionCall(potentialDependency, functionName, analysisResult)) {
                dependencies.add(potentialDependency);
            }
        }
        
        return dependencies;
    }
    
    /**
     * Check if a function call is a relevant dependency
     */
    private boolean isRelevantFunctionCall(
            String calledFunc, String currentFunc, ApplicationAnalysisResult analysisResult) {
        Map<String, Map<String, String>> functionsMap = analysisResult.getFunctions();
        if (functionsMap == null) {
            return false;
        }
        
        Map<String, String> calledFuncInfo = functionsMap.get(calledFunc);
        Map<String, String> currentFuncInfo = functionsMap.get(currentFunc);
        
        if (calledFuncInfo == null || currentFuncInfo == null) {
            return false;
        }
        String calledFile = calledFuncInfo.get("file");
        String currentFile = currentFuncInfo.get("file");
        if (calledFile == null || currentFile == null) {
            return true;
        }
        
        return !calledFile.equals(currentFile);
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
                Pattern pattern = Pattern.compile("\\s*([\\w_]+)\\s*=\\s*Flask\\(.*\\)");
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
        return "app";
    }

    /**
     * Extract imports for a specific function based on its file path
     */
    private List<Function.ImportDefinition> extractImportsForFunction(String filePath, ApplicationAnalysisResult analysisResult) {
        if (filePath == null || analysisResult.getImports() == null) {
            return new ArrayList<>();
        }
        List<Function.ImportDefinition> fileImports = analysisResult.getImports().get(filePath);
        if (fileImports != null) {
            logger.debug("Found {} imports for file {}", fileImports.size(), filePath);
            return new ArrayList<>(fileImports);
        }
        
        logger.debug("No imports found for file {}", filePath);
        return new ArrayList<>();
    }

    /**
     * Validate application path
     */
    private void validateAppPath(String appPath) {
        if (appPath == null || appPath.isEmpty()) {
            throw new BusinessRuleException("Application path cannot be null or empty");
        }

        File appDir = new File(appPath);
        if (!appDir.exists()) {
            throw new ResourceNotFoundException("App directory does not exist: " + appPath);
        }
        if (!appDir.isDirectory()) {
            throw new BusinessRuleException("Application path is not a directory: " + appPath);
        }
    }
}