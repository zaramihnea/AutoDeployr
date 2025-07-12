package com.infrastructure.services.transformer;

import com.domain.entities.Function;
import com.domain.entities.FunctionBuildContext;
import com.infrastructure.exceptions.FileOperationException;
import com.infrastructure.exceptions.TemplateException;
import com.infrastructure.services.template.TemplateService;
import com.infrastructure.services.template.TemplateConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Serverless PHP Function Transformer - Direct execution like Java (no web server)
 * This transformer creates PHP functions that execute directly via CLI and output JSON to stdout
 */
@Component
public class PhpServerlessFunctionTransformer extends AbstractFunctionTransformer {
    private static final Logger logger = LoggerFactory.getLogger(PhpServerlessFunctionTransformer.class);

    private final TemplateService templateService;

    @Autowired
    public PhpServerlessFunctionTransformer(TemplateService templateService) {
        super("php", "laravel");
        this.templateService = templateService;
        logger.info("PHP Serverless Function Transformer initialized (Java-like execution model)");
    }

    @Override
    public String createMainApplication(FunctionBuildContext context) {
        Function function = context.getFunction();
        Path buildPath = context.getBuildPath();
        
        logger.info("Creating serverless PHP function (like Java): {}", function.getName());

        try {
            Path functionPath = buildPath.resolve("function.php");
            
            Map<String, String> variables = new HashMap<>();
            variables.put("FUNCTION_NAME", function.getName());
            variables.put("ROUTE", function.getPath());
            variables.put("HTTP_METHOD", function.getMethods() != null && !function.getMethods().isEmpty() ? 
                function.getMethods().get(0).toLowerCase() : "get");
            variables.put("FUNCTION_BODY", generateServerlessFunctionBody(function));
            
            logger.info("Creating serverless PHP function for '{}' with variables:", function.getName());
            logger.info("  FUNCTION_NAME: {}", variables.get("FUNCTION_NAME"));
            logger.info("  ROUTE: {}", variables.get("ROUTE"));
            logger.info("  HTTP_METHOD: {}", variables.get("HTTP_METHOD"));
            logger.info("  FUNCTION_BODY: {}", variables.get("FUNCTION_BODY"));
            
            String functionContent = templateService.processTemplate(
                TemplateConstants.PHP_SERVERLESS_FUNCTION_TEMPLATE, 
                variables
            );
            
            Files.write(functionPath, functionContent.getBytes());
            logger.info("Created serverless function.php at: {}", functionPath);
            
            return functionContent;
            
        } catch (IOException | TemplateException e) {
            logger.error("Failed to create serverless PHP function: {}", e.getMessage());
            throw new FileOperationException("Failed to create serverless PHP function", e);
        }
    }

    @Override
    public String createFunctionWrapper(FunctionBuildContext context) {
        logger.info("Serverless mode: function.php serves as both function and wrapper (like Java)");
        return createMainApplication(context);
    }

    @Override
    public String createDependencyFile(FunctionBuildContext context) {
        Path buildPath = context.getBuildPath();
        
        logger.info("Creating composer.json for serverless PHP dependencies");

        try {
            Path composerPath = buildPath.resolve("composer.json");
            Path appPath = context.getAppPath();
            Path originalComposerPath = appPath.resolve("composer.json");
            if (Files.exists(originalComposerPath)) {
                Files.copy(originalComposerPath, composerPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied original composer.json from Laravel app");
                Path originalComposerLock = appPath.resolve("composer.lock");
                if (Files.exists(originalComposerLock)) {
                    Path composerLockPath = buildPath.resolve("composer.lock");
                    Files.copy(originalComposerLock, composerLockPath, StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Copied composer.lock from Laravel app");
                }
            } else {
                Map<String, String> variables = new HashMap<>();
                String sanitizedName = context.getFunction().getName()
                    .replaceAll("[^a-zA-Z0-9_-]", "-")
                    .toLowerCase();
                variables.put("FUNCTION_NAME", sanitizedName);
                
                String composerContent = templateService.processTemplate(
                    TemplateConstants.PHP_COMPOSER_TEMPLATE, 
                    variables
                );
                
                Files.write(composerPath, composerContent.getBytes());
                logger.info("Generated composer.json from template");
            }
            
            return "composer.json created successfully";
            
        } catch (IOException | TemplateException e) {
            logger.error("Failed to create composer.json: {}", e.getMessage());
            throw new FileOperationException("Failed to create composer.json", e);
        }
    }

    @Override
    public String createDockerfile(FunctionBuildContext context) {
        Function function = context.getFunction();
        Path buildPath = context.getBuildPath();
        
        logger.info("Creating serverless Dockerfile for PHP function: {}", function.getName());

        try {
            Path dockerfilePath = buildPath.resolve("Dockerfile");
            
            Map<String, String> variables = new HashMap<>();
            variables.put("FUNCTION_NAME", function.getName());
            String dockerfileContent = templateService.processTemplate(
                TemplateConstants.PHP_SERVERLESS_DOCKERFILE_TEMPLATE, 
                variables
            );
            
            Files.write(dockerfilePath, dockerfileContent.getBytes());
            logger.info("Created serverless Dockerfile at: {}", dockerfilePath);
            
            return dockerfileContent;
            
        } catch (IOException | TemplateException e) {
            logger.error("Failed to create serverless Dockerfile: {}", e.getMessage());
            throw new FileOperationException("Failed to create serverless Dockerfile", e);
        }
    }

    @Override
    public void createSupportFiles(FunctionBuildContext context) throws IOException {
        Function function = context.getFunction();
        Path appPath = context.getAppPath();
        Path buildPath = context.getBuildPath();
        
        logger.info("Creating support files for serverless PHP function: {}", function.getName());

        try {
            copyMinimalLaravelFiles(appPath, buildPath);
            copyControllerFiles(function, appPath, buildPath);
            copyModelFiles(appPath, buildPath);
            createServerlessEnvFile(buildPath);
            
            logger.info("Serverless support files created successfully for function: {}", function.getName());
            
        } catch (Exception e) {
            logger.error("Failed to create serverless support files: {}", e.getMessage());
            throw new IOException("Failed to create serverless support files", e);
        }
    }

    /**
     * Generate serverless function body (optimized for direct execution)
     */
    private String generateServerlessFunctionBody(Function function) {
        String source = function.getSource();
        String name = function.getName();
        
        logger.info("=== Generating serverless function body for: {} ===", name);
        logger.info("Original source: {}", source);
        
        if (source != null && !source.trim().isEmpty()) {
            source = source.trim();
            if (source.startsWith("function") || source.contains("function (")) {
                logger.info("Source is already a complete function/closure");
                return source;
            }
            
            if (source.startsWith("return ")) {
                String result = "function () {\n    " + source + "\n}";
                logger.info("Wrapping return statement in serverless closure");
                return result;
            }
            
            if (source.contains("return ")) {
                String result = "function () {\n    " + source + "\n}";
                logger.info("Wrapping closure body for serverless execution");
                return result;
            }
            String result = "function () {\n    return " + source + ";\n}";
            logger.info("Wrapping source in serverless closure");
            return result;
        } else {
            String defaultBody = "function () {\n    return response()->json([\n        'message' => 'Serverless function " + name + " executed successfully',\n        'timestamp' => now(),\n        'mode' => 'serverless'\n    ]);\n}";
            logger.info("Using default serverless function body");
            return defaultBody;
        }
    }

    /**
     * Copy minimal Laravel files for serverless execution
     */
    private void copyMinimalLaravelFiles(Path appPath, Path buildPath) throws IOException {
        logger.debug("Copying minimal Laravel files for serverless execution");
        Files.createDirectories(buildPath.resolve("bootstrap/cache"));
        Path configSrc = appPath.resolve("config");
        if (Files.exists(configSrc)) {
            Path configDest = buildPath.resolve("config");
            copyDirectoryIfExists(configSrc, configDest);
        }
        Path envSrc = appPath.resolve(".env");
        if (Files.exists(envSrc)) {
            Path envDest = buildPath.resolve(".env");
            Files.copy(envSrc, envDest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Create optimized .env file for serverless execution
     */
    private void createServerlessEnvFile(Path buildPath) throws IOException {
        Path envFile = buildPath.resolve(".env");
        if (!Files.exists(envFile)) {
            String serverlessEnv = "APP_ENV=production\n" +
                                 "APP_DEBUG=false\n" +
                                 "APP_KEY=base64:" + java.util.Base64.getEncoder().encodeToString("ServerlessLaravel12345678901234567890".getBytes()) + "\n" +
                                 "LOG_CHANNEL=stderr\n" +
                                 "CACHE_DRIVER=array\n" +
                                 "SESSION_DRIVER=array\n";
            Files.write(envFile, serverlessEnv.getBytes());
            logger.debug("Created serverless optimized .env file");
        }
    }

    /**
     * Copy controller files if the function references a controller
     */
    private void copyControllerFiles(Function function, Path appPath, Path buildPath) throws IOException {
        if (function.getSource().contains("Controller")) {
            logger.debug("Copying controller files for serverless function: {}", function.getName());
            
            Path controllersSrc = appPath.resolve("app/Http/Controllers");
            if (Files.exists(controllersSrc)) {
                Path controllersDest = buildPath.resolve("app/Http/Controllers");
                copyDirectoryIfExists(controllersSrc, controllersDest);
            }
        }
    }

    /**
     * Copy model files that might be referenced
     */
    private void copyModelFiles(Path appPath, Path buildPath) throws IOException {
        Path modelsSrc = appPath.resolve("app/Models");
        if (Files.exists(modelsSrc)) {
            logger.debug("Copying model files for serverless execution");
            Path modelsDest = buildPath.resolve("app/Models");
            copyDirectoryIfExists(modelsSrc, modelsDest);
        }
    }

    /**
     * Helper method to copy a directory if it exists
     */
    private void copyDirectoryIfExists(Path source, Path destination) throws IOException {
        if (Files.exists(source)) {
            Files.createDirectories(destination.getParent());
            FileUtils.copyDirectory(source.toFile(), destination.toFile());
            logger.debug("Copied directory from {} to {}", source, destination);
        }
    }
} 