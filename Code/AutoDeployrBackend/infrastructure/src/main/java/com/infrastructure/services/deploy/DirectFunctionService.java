package com.infrastructure.services.deploy;

import com.domain.exceptions.ValidationException;
import com.infrastructure.exceptions.FileOperationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for creating temporary application structures from direct function code
 */
@Service
@RequiredArgsConstructor
public class DirectFunctionService {
    private static final Logger logger = LoggerFactory.getLogger(DirectFunctionService.class);
    private final Map<String, AtomicInteger> languageCounters = new ConcurrentHashMap<>();

    /**
     * Create a temporary application structure from direct function code
     * with sequential naming
     *
     * @param appName Name of the application (optional, can be null)
     * @param language Programming language (python, java, etc.)
     * @param functionCode Source code for the function
     * @return Path to the temporary application directory
     * @throws FileOperationException If file operations fail
     * @throws ValidationException If parameters are invalid
     */
    public String createTempApp(String appName, String language, String functionCode) {
        String functionName = "handler";
        String route = "/";
        String httpMethod = "GET";

        validateParameters(appName, language, functionName, functionCode, route, httpMethod);

        try {
            String finalAppName;
            if (appName != null && !appName.trim().isEmpty()) {
                finalAppName = sanitizeAppName(appName.trim());
                logger.info("Using provided application name: {}", finalAppName);
            } else {
                String sanitizedLang = language.toLowerCase().replaceAll("[^a-z]", "");
                AtomicInteger counter = languageCounters.computeIfAbsent(
                        sanitizedLang, k -> new AtomicInteger(0)
                );
                int appNumber = counter.incrementAndGet();
                finalAppName = sanitizedLang + "_app_" + appNumber;
                logger.info("Generated sequential application name: {}", finalAppName);
            }
            Path tempDir = Files.createTempDirectory("direct_" + finalAppName);
            logger.info("Created temporary directory for direct function: {}", tempDir);
            tempDir.toFile().deleteOnExit();
            switch (language.toLowerCase()) {
                case "python":
                    createPythonAppStructure(tempDir, functionName, functionCode, route, httpMethod);
                    break;
                case "java":
                    createJavaAppStructure(tempDir, functionName, functionCode, route, httpMethod);
                    break;
                case "php":
                    createPhpAppStructure(tempDir, functionName, functionCode, route, httpMethod);
                    break;
                default:
                    throw new ValidationException("language", "Unsupported language: " + language);
            }

            logger.info("Created {} application with sequential name: {}", language, finalAppName);
            return tempDir.toString();
        } catch (IOException e) {
            logger.error("Error creating temporary app structure: {}", e.getMessage(), e);
            throw new FileOperationException("create", "temp directory",
                    "Failed to create temporary app structure: " + e.getMessage(), e);
        }
    }

    /**
     * Validate input parameters for creating a function
     */
    private void validateParameters(String appName, String language, String functionName,
                                  String functionCode, String route, String httpMethod) {
        if (language == null || language.trim().isEmpty()) {
            throw new ValidationException("language", "Language cannot be empty");
        }
        if (functionName == null || functionName.trim().isEmpty()) {
            throw new ValidationException("functionName", "Function name cannot be empty");
        }
        if (functionCode == null || functionCode.trim().isEmpty()) {
            throw new ValidationException("functionCode", "Function code cannot be empty");
        }
        if (route == null || route.trim().isEmpty()) {
            throw new ValidationException("route", "Route path cannot be empty");
        }
        if (httpMethod == null || httpMethod.trim().isEmpty()) {
            throw new ValidationException("httpMethod", "HTTP method cannot be empty");
        }
    }

    /**
     * Sanitize application name to ensure it meets naming requirements
     *
     * @param appName Raw application name
     * @return Sanitized application name
     */
    private String sanitizeAppName(String appName) {
        String sanitized = appName.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (!sanitized.isEmpty() && !Character.isLetterOrDigit(sanitized.charAt(0))) {
            sanitized = "app_" + sanitized;
        }
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        
        logger.debug("Sanitized application name: {} -> {}", appName, sanitized);
        return sanitized;
    }

    /**
     * Create a Python Flask application structure for the direct function
     */
    private void createPythonAppStructure(Path tempDir, String functionName,
                                          String functionCode, String route, String httpMethod) throws IOException {
        logger.info("Creating Python Flask app structure in {}", tempDir);

        // Create app.py with Flask application
        String httpMethodUpper = httpMethod.toUpperCase();

        // Create Flask application with the function
        StringBuilder appPy = new StringBuilder();
        appPy.append("from flask import Flask, request, jsonify\n\n");
        appPy.append("app = Flask(__name__)\n\n");

        // Check if the function code already has a route decorator
        if (!functionCode.contains("@app.route")) {
            appPy.append("@app.route('").append(route).append("'");
            if (!"GET".equals(httpMethodUpper)) {
                appPy.append(", methods=['").append(httpMethodUpper).append("']");
            }
            appPy.append(")\n");
        }

        // Add the function code
        appPy.append(functionCode).append("\n\n");

        // Add the main block to run the app when executed directly
        appPy.append("if __name__ == '__main__':\n");
        appPy.append("    app.run(debug=True)\n");

        // Write app.py file
        Files.writeString(tempDir.resolve("app.py"), appPy.toString());

        // Create requirements.txt
        Files.writeString(tempDir.resolve("requirements.txt"), "flask==2.0.1\n");

        logger.info("Created Python Flask app structure with function: {}", functionName);
    }

    /**
     * Create a simple Java application structure for the direct function (no Spring Boot)
     */
    private void createJavaAppStructure(Path tempDir, String functionName,
                                        String functionCode, String route, String httpMethod) throws IOException {
        logger.info("Creating simple Java app structure in {}", tempDir);

        // Create directory structure
        Path srcMainJava = tempDir.resolve(Paths.get("src", "main", "java", "com", "example"));
        Files.createDirectories(srcMainJava);

        // Create simple function class
        String className = functionName.substring(0, 1).toUpperCase() + functionName.substring(1);

        StringBuilder javaCode = new StringBuilder();
        javaCode.append("package com.example;\n\n");
        javaCode.append("import java.util.Map;\n");
        javaCode.append("import java.util.HashMap;\n\n");

        javaCode.append("public class ").append(className).append(" {\n\n");

        // Add the function code as a static method that accepts Map<String, Object> event
        javaCode.append("    public static Object ").append(functionName).append("(Map<String, Object> event) {\n");
        
        // Add the function code, indenting it with 8 spaces and adapting it
        String adaptedCode = adaptDirectFunctionCode(functionCode);
        for (String line : adaptedCode.split("\n")) {
            javaCode.append("        ").append(line).append("\n");
        }
        
        javaCode.append("    }\n");
        javaCode.append("}\n");

        Files.writeString(srcMainJava.resolve(className + ".java"), javaCode.toString());

        // Create simple pom.xml without Spring Boot
        StringBuilder pomXml = new StringBuilder();
        pomXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        pomXml.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        pomXml.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        pomXml.append("    <modelVersion>4.0.0</modelVersion>\n");
        pomXml.append("    <groupId>com.example</groupId>\n");
        pomXml.append("    <artifactId>direct-function</artifactId>\n");
        pomXml.append("    <version>1.0.0</version>\n");
        pomXml.append("    <name>").append(functionName).append("</name>\n");
        pomXml.append("    <description>Direct function deployment</description>\n\n");
        pomXml.append("    <properties>\n");
        pomXml.append("        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n");
        pomXml.append("        <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>\n");
        pomXml.append("        <java.version>17</java.version>\n");
        pomXml.append("        <maven.compiler.source>17</maven.compiler.source>\n");
        pomXml.append("        <maven.compiler.target>17</maven.compiler.target>\n");
        pomXml.append("    </properties>\n\n");
        pomXml.append("    <dependencies>\n");
        pomXml.append("        <!-- Jackson for JSON handling -->\n");
        pomXml.append("        <dependency>\n");
        pomXml.append("            <groupId>com.fasterxml.jackson.core</groupId>\n");
        pomXml.append("            <artifactId>jackson-databind</artifactId>\n");
        pomXml.append("            <version>2.15.2</version>\n");
        pomXml.append("        </dependency>\n");
        pomXml.append("        <!-- SLF4J for logging -->\n");
        pomXml.append("        <dependency>\n");
        pomXml.append("            <groupId>org.slf4j</groupId>\n");
        pomXml.append("            <artifactId>slf4j-api</artifactId>\n");
        pomXml.append("            <version>2.0.9</version>\n");
        pomXml.append("        </dependency>\n");
        pomXml.append("        <dependency>\n");
        pomXml.append("            <groupId>ch.qos.logback</groupId>\n");
        pomXml.append("            <artifactId>logback-classic</artifactId>\n");
        pomXml.append("            <version>1.4.14</version>\n");
        pomXml.append("        </dependency>\n");
        pomXml.append("    </dependencies>\n\n");
        pomXml.append("    <build>\n");
        pomXml.append("        <plugins>\n");
        pomXml.append("            <plugin>\n");
        pomXml.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        pomXml.append("                <artifactId>maven-compiler-plugin</artifactId>\n");
        pomXml.append("                <version>3.11.0</version>\n");
        pomXml.append("                <configuration>\n");
        pomXml.append("                    <source>17</source>\n");
        pomXml.append("                    <target>17</target>\n");
        pomXml.append("                </configuration>\n");
        pomXml.append("            </plugin>\n");
        pomXml.append("            <plugin>\n");
        pomXml.append("                <groupId>org.apache.maven.plugins</groupId>\n");
        pomXml.append("                <artifactId>maven-shade-plugin</artifactId>\n");
        pomXml.append("                <version>3.4.1</version>\n");
        pomXml.append("                <executions>\n");
        pomXml.append("                    <execution>\n");
        pomXml.append("                        <phase>package</phase>\n");
        pomXml.append("                        <goals>\n");
        pomXml.append("                            <goal>shade</goal>\n");
        pomXml.append("                        </goals>\n");
        pomXml.append("                    </execution>\n");
        pomXml.append("                </executions>\n");
        pomXml.append("            </plugin>\n");
        pomXml.append("        </plugins>\n");
        pomXml.append("    </build>\n");
        pomXml.append("</project>\n");

        Files.writeString(tempDir.resolve("pom.xml"), pomXml.toString());

        logger.info("Created simple Java app structure with function: {}", functionName);
    }

    /**
     * Adapt direct function code to work with event-based invocation
     */
    private String adaptDirectFunctionCode(String functionCode) {
        if (!functionCode.contains("return")) {
            return functionCode + "\n        return Map.of(\"result\", \"Function executed successfully\");";
        }
        return functionCode;
    }

    /**
     * Create a PHP Laravel application structure for the direct function
     */
    private void createPhpAppStructure(Path tempDir, String functionName,
                                      String functionCode, String route, String httpMethod) throws IOException {
        logger.info("Creating PHP Laravel app structure in {}", tempDir);

        // Create basic Laravel structure
        Path appDir = tempDir.resolve("app");
        Path httpDir = appDir.resolve("Http");
        Path controllersDir = httpDir.resolve("Controllers");
        Path routesDir = tempDir.resolve("routes");
        
        Files.createDirectories(controllersDir);
        Files.createDirectories(routesDir);

        // Create Controller class
        String controllerName = functionName.substring(0, 1).toUpperCase() + functionName.substring(1) + "Controller";
        
        StringBuilder controllerCode = new StringBuilder();
        controllerCode.append("<?php\n\n");
        controllerCode.append("namespace App\\Http\\Controllers;\n\n");
        controllerCode.append("use Illuminate\\Http\\Request;\n");
        controllerCode.append("use Illuminate\\Http\\JsonResponse;\n\n");
        
        controllerCode.append("class ").append(controllerName).append(" extends Controller\n");
        controllerCode.append("{\n");
        
        // Add the function method
        controllerCode.append("    public function ").append(functionName).append("(Request $request): JsonResponse\n");
        controllerCode.append("    {\n");
        
        // Add the function code, indenting it with 8 spaces
        for (String line : functionCode.split("\n")) {
            controllerCode.append("        ").append(line).append("\n");
        }
        
        controllerCode.append("    }\n");
        controllerCode.append("}\n");

        Files.writeString(controllersDir.resolve(controllerName + ".php"), controllerCode.toString());

        // Create routes file
        StringBuilder routesCode = new StringBuilder();
        routesCode.append("<?php\n\n");
        routesCode.append("use Illuminate\\Support\\Facades\\Route;\n");
        routesCode.append("use App\\Http\\Controllers\\").append(controllerName).append(";\n\n");
        
        // Add route
        String httpMethodLower = httpMethod.toLowerCase();
        routesCode.append("Route::").append(httpMethodLower).append("('").append(route).append("', [")
                  .append(controllerName).append("::class, '").append(functionName).append("']);\n");

        Files.writeString(routesDir.resolve("web.php"), routesCode.toString());

        // Create basic composer.json
        StringBuilder composerJson = new StringBuilder();
        composerJson.append("{\n");
        composerJson.append("    \"name\": \"laravel/laravel\",\n");
        composerJson.append("    \"type\": \"project\",\n");
        composerJson.append("    \"description\": \"Direct function deployment\",\n");
        composerJson.append("    \"require\": {\n");
        composerJson.append("        \"php\": \"^8.0\",\n");
        composerJson.append("        \"laravel/framework\": \"^9.0\"\n");
        composerJson.append("    },\n");
        composerJson.append("    \"autoload\": {\n");
        composerJson.append("        \"psr-4\": {\n");
        composerJson.append("            \"App\\\\\": \"app/\"\n");
        composerJson.append("        }\n");
        composerJson.append("    }\n");
        composerJson.append("}\n");

        Files.writeString(tempDir.resolve("composer.json"), composerJson.toString());

        StringBuilder artisanFile = new StringBuilder();
        artisanFile.append("#!/usr/bin/env php\n");
        artisanFile.append("<?php\n");
        artisanFile.append("// Laravel Artisan CLI placeholder\n");

        Files.writeString(tempDir.resolve("artisan"), artisanFile.toString());

        logger.info("Created PHP Laravel app structure with function: {}", functionName);
    }

    /**
     * Clean up temporary directory
     */
    public void cleanupTempDirectory(String tempDirPath) {
        if (tempDirPath == null) {
            return;
        }

        try {
            Path path = Path.of(tempDirPath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                Files.walk(path)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                logger.warn("Failed to delete {}: {}", p, e.getMessage());
                            }
                        });
                logger.info("Cleaned up temporary directory: {}", tempDirPath);
            }
        } catch (IOException e) {
            logger.error("Error cleaning up temporary directory: {}", e.getMessage(), e);
        }
    }
}