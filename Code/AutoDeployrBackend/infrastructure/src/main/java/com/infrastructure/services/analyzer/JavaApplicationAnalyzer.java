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
import java.util.Arrays;
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
 * Analyzer for Java Spring applications
 */
@Component
public class JavaApplicationAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(JavaApplicationAnalyzer.class);

    private static final String CONTROLLER_PATTERN = "@(?:Rest)?Controller";
    private static final String MAPPING_PATTERN = "@(?:Request|Get|Post|Put|Delete|Patch)Mapping\\s*(?:\\(\\s*(?:value\\s*=\\s*)?[\"']([^\"']+)[\"'](?:.*?)\\))?";
    private static final String METHOD_PATTERN = "@(?:(Get|Post|Put|Delete|Patch)Mapping)|@RequestMapping\\s*\\(.*?method\\s*=\\s*(?:RequestMethod\\.)?([A-Z_]+)";
    private static final String JAVA_PACKAGE_PATTERN = "package\\s+([\\w.]+);";
    private static final String JAVA_CLASS_PATTERN = "(?:public|private|protected)?\\s*(?:static)?\\s*class\\s+(\\w+)";
    private static final String JAVA_METHOD_PATTERN = "(?:public|private|protected)?\\s*(?:static)?\\s*\\w+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w,\\s.]+)?\\s*\\{";

    /**
     * Analyze Java application
     *
     * @param appPath Application path
     * @return Analysis result
     */
    public ApplicationAnalysisResult analyze(String appPath) {
        logger.info("Analyzing Java application in path: {}", appPath);

        try {
            // Initialize result with basic information
            ApplicationAnalysisResult result = new ApplicationAnalysisResult();
            result.setLanguage("java");
            result.setFramework("spring");

            // Collect Java files for analysis
            List<File> javaFiles = findJavaFiles(appPath);
            if (javaFiles.isEmpty()) {
                throw new BusinessRuleException("No Java files found in the application");
            }

            // Find the Spring application name
            String springAppName = findSpringAppName(javaFiles);
            result.setAppName(springAppName);

            // Use JavaCodeAnalyzer for detailed analysis
            JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer(appPath);
            List<Function> analyzedFunctions = analyzer.analyze();
            List<Route> routes = analyzer.findRoutes();
            
            // Set routes and process functions
            result.setRoutes(routes);
            result.setFunctions(processFunctionsForResult(analyzedFunctions));
            
            // Extract environment variables from functions
            result.setEnvVars(extractEnvironmentVariables(javaFiles));
            
            logger.info("Java application analysis completed successfully with {} functions and {} routes",
                    routes.size(), routes.size());
            return result;
        } catch (BusinessRuleException e) {
            // Re-throw domain exceptions
            throw e;
        } catch (Exception e) {
            logger.error("Error analyzing Java application: {}", e.getMessage(), e);
            throw new CodeAnalysisException("java", "Failed to analyze Java application: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process functions for inclusion in the analysis result
     * 
     * @param functions List of functions to process
     * @return Map of function names to function information
     */
    private Map<String, Map<String, String>> processFunctionsForResult(List<Function> functions) {
        Map<String, Map<String, String>> result = new HashMap<>();
        
        for (Function function : functions) {
            Map<String, String> functionInfo = new HashMap<>();
            functionInfo.put("source", function.getSource());
            functionInfo.put("file", function.getFilePath());
            result.put(function.getName(), functionInfo);
        }
        
        return result;
    }
    
    /**
     * Extract all environment variables used by functions
     * 
     * @param javaFiles List of Java files to analyze
     * @return Set of environment variable names
     */
    private Set<String> extractEnvironmentVariables(List<File> javaFiles) {
        Set<String> envVars = new HashSet<>();
        Pattern valuePattern = Pattern.compile("@Value\\(\\s*\"\\$\\{([^:}]+)(?::.*?)?\\}\"\\s*\\)");
        
        for (File file : javaFiles) {
            try {
                String content = Files.readString(file.toPath());
                Matcher matcher = valuePattern.matcher(content);
                
                while (matcher.find()) {
                    String envVar = matcher.group(1);
                    envVars.add(envVar);
                    logger.debug("Found environment variable: {}", envVar);
                }
            } catch (IOException e) {
                logger.error("Error extracting environment variables from {}: {}", file, e.getMessage(), e);
                // Continue with other files
            }
        }
        
        return envVars;
    }

    /**
     * Find Spring application name in Java files
     */
    private String findSpringAppName(List<File> javaFiles) {
        for (File file : javaFiles) {
            try {
                String content = Files.readString(file.toPath());
                Pattern pattern = Pattern.compile("@SpringBootApplication\\s*public\\s*class\\s*(\\w+)");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    String appName = matcher.group(1);
                    logger.info("Found Spring application name: {}", appName);
                    return appName;
                }
            } catch (IOException e) {
                logger.error("Error reading file {}: {}", file, e.getMessage(), e);
                throw new CodeAnalysisException("java", "Error reading file: " + file, e);
            }
        }
        return "Application";
    }

    /**
     * Find Java files in the application
     */
    private List<File> findJavaFiles(String appPath) {
        List<File> javaFiles = new ArrayList<>();

        try {
            try (Stream<Path> paths = Files.walk(Path.of(appPath))) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(p -> javaFiles.add(p.toFile()));
            }
        } catch (IOException e) {
            logger.error("Error finding Java files: {}", e.getMessage(), e);
            throw new CodeAnalysisException("java", "Error finding Java files: " + e.getMessage(), e);
        }

        return javaFiles;
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