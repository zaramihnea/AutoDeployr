package com.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.analyzer.model.AnalysisResult;
import com.analyzer.model.SpringEndpoint;
import com.analyzer.visitor.ControllerVisitor;
import com.analyzer.visitor.DependencyVisitor;
import com.analyzer.visitor.EndpointVisitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Main Spring application analyzer class.
 * Analyzes a Spring Boot application to extract controllers, endpoints, and dependencies.
 */
public class SpringApplicationAnalyzer {
    private final String appPath;
    private final ObjectMapper objectMapper;
    private final JavaParser javaParser;

    /**
     * Create a new Spring application analyzer.
     *
     * @param appPath Path to the Spring Boot application directory
     */
    public SpringApplicationAnalyzer(String appPath) {
        this.appPath = appPath;
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
        combinedSolver.add(new ReflectionTypeSolver());

        try {
            combinedSolver.add(new JavaParserTypeSolver(new File(appPath)));
            File srcMainJava = new File(appPath, "src/main/java");
            if (srcMainJava.exists() && srcMainJava.isDirectory()) {
                combinedSolver.add(new JavaParserTypeSolver(srcMainJava));
                System.err.println("Added src/main/java directory to type solver");
            }
        } catch (Exception e) {
            System.err.println("Failed to add application source to type solver: " + e.getMessage());
        }

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedSolver);
        this.javaParser = new JavaParser();
        this.javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);

        System.err.println("Initialized Spring application analyzer for path: " + appPath);
    }

    /**
     * Analyze the Spring Boot application.
     *
     * @return Analysis result containing endpoints and dependencies
     * @throws Exception If analysis fails
     */
    public AnalysisResult analyze() throws Exception {
        System.err.println("Starting analysis of Spring application at: " + appPath);

        List<File> javaFiles = findJavaFiles();
        System.err.println("Found " + javaFiles.size() + " Java files to analyze");

        List<SpringEndpoint> endpoints = new ArrayList<>();
        Map<String, String> serviceSources = new HashMap<>();
        Set<String> environmentVariables = new HashSet<>();

        // First pass: Find all controllers and their endpoints
        for (File file : javaFiles) {
            try {
                ParseResult<CompilationUnit> parseResult = javaParser.parse(file);
                if (parseResult.isSuccessful()) {
                    CompilationUnit cu = parseResult.getResult().get();

                    // Visit controllers and collect endpoints
                    ControllerVisitor controllerVisitor = new ControllerVisitor();
                    controllerVisitor.visit(cu, null);

                    // If any endpoints found in this file
                    if (!controllerVisitor.getEndpoints().isEmpty()) {
                        System.err.println("Found " + controllerVisitor.getEndpoints().size() + " endpoints in file: " + file.getName());
                        endpoints.addAll(controllerVisitor.getEndpoints());
                    }

                    // Collect environment variables from all files using EndpointVisitor
                    EndpointVisitor endpointVisitor = new EndpointVisitor();
                    endpointVisitor.visit(cu, null);
                    Set<String> fileEnvVars = endpointVisitor.getEnvironmentVariables();
                    if (!fileEnvVars.isEmpty()) {
                        System.err.println("Found " + fileEnvVars.size() + " environment variables in file: " + file.getName());
                        environmentVariables.addAll(fileEnvVars);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error parsing file " + file.getName() + ": " + e.getMessage());
            }
        }

        // Second pass: Analyze dependencies between endpoints and service methods
        if (!endpoints.isEmpty()) {
            System.err.println("Analyzing dependencies for " + endpoints.size() + " endpoints");
            DependencyVisitor dependencyVisitor = new DependencyVisitor(javaFiles, javaParser);

            for (SpringEndpoint endpoint : endpoints) {
                Set<String> dependencies = dependencyVisitor.findDependencies(endpoint);
                endpoint.setDependencies(dependencies);
                System.err.println("Found " + dependencies.size() + " dependencies for endpoint: " + endpoint.getName());

                // Collect source code for dependencies
                for (String dependency : dependencies) {
                    if (!serviceSources.containsKey(dependency)) {
                        String source = dependencyVisitor.getSourceForMethod(dependency);
                        if (source != null && !source.trim().isEmpty()) {
                            serviceSources.put(dependency, source);
                            System.err.println("Added source for dependency: " + dependency);
                        }
                    }
                }
            }
        } else {
            System.err.println("No endpoints found in application");
        }

        // Build the analysis result
        AnalysisResult result = new AnalysisResult();
        result.setLanguage("java");
        result.setFramework("spring");
        result.setEndpoints(endpoints);
        result.setServiceSources(serviceSources);
        result.setEnvironmentVariables(environmentVariables);

        System.err.println("Analysis completed. Found " + endpoints.size() + " endpoints, " + serviceSources.size() + " dependencies, and " + environmentVariables.size() + " environment variables");

        return result;
    }

    /**
     * Write analysis results to a JSON file.
     *
     * @param outputFile Path to output file
     * @throws Exception If writing fails
     */
    public void writeAnalysisToFile(String outputFile) throws Exception {
        AnalysisResult result = analyze();
        objectMapper.writeValue(System.out, result);
        System.err.println("Analysis results written to: " + outputFile);
    }

    /**
     * Find all Java files in the application directory.
     *
     * @return List of Java files
     * @throws Exception If file operations fail
     */
    private List<File> findJavaFiles() throws Exception {
        try (Stream<Path> paths = Files.walk(Paths.get(appPath))) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Main method for running the analyzer from the command line.
     *
     * @param args Command line arguments: appPath [outputFile]
     */
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("Usage: java SpringApplicationAnalyzer <app-path> [output-file]");
                System.exit(1);
            }

            String appPath = args[0];
            String outputFile = args.length > 1 ? args[1] : null;

            SpringApplicationAnalyzer analyzer = new SpringApplicationAnalyzer(appPath);
            AnalysisResult result = analyzer.analyze();
            ObjectMapper stdoutMapper = new ObjectMapper();
            
            if (outputFile != null) {
                analyzer.writeAnalysisToFile(outputFile);
                System.err.println("Analysis results written to: " + outputFile);
            } else {
                System.out.println(stdoutMapper.writeValueAsString(result));
            }
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}