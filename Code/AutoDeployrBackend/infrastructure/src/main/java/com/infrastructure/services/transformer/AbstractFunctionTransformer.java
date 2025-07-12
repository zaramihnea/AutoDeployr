package com.infrastructure.services.transformer;

import com.domain.entities.Function;
import com.domain.entities.FunctionBuildContext;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.exceptions.ValidationException;
import com.infrastructure.exceptions.CodeAnalysisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base abstract class for function transformers
 */
public abstract class AbstractFunctionTransformer {
    private static final Logger logger = LoggerFactory.getLogger(AbstractFunctionTransformer.class);

    protected final String language;
    protected final String framework;

    /**
     * Create a new function transformer
     *
     * @param language Programming language
     * @param framework Framework
     */
    public AbstractFunctionTransformer(String language, String framework) {
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("Language cannot be empty");
        }

        if (framework == null || framework.trim().isEmpty()) {
            throw new IllegalArgumentException("Framework cannot be empty");
        }

        this.language = language;
        this.framework = framework;
    }

    /**
     * Get the language identifier
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Get the framework identifier
     */
    public String getFramework() {
        return framework;
    }

    /**
     * Create all files for a serverless function
     *
     * @param function Function to transform
     * @param appPath Original application path
     * @param buildPath Build directory path
     * @return Success status
     * @throws ValidationException If the function is invalid
     * @throws ResourceNotFoundException If paths don't exist
     * @throws CodeAnalysisException If transformation fails
     */
    public boolean createServerlessFunction(Function function, String appPath, String buildPath) {
        if (function == null) {
            throw new ValidationException("function", "Function cannot be null");
        }
        function.validate();

        if (appPath == null || appPath.trim().isEmpty()) {
            throw new ValidationException("appPath", "Application path cannot be empty");
        }

        if (buildPath == null || buildPath.trim().isEmpty()) {
            throw new ValidationException("buildPath", "Build path cannot be empty");
        }
        File appDir = new File(appPath);
        if (!appDir.exists() || !appDir.isDirectory()) {
            throw new ResourceNotFoundException("Application directory", appPath);
        }

        try {
            File buildDir = new File(buildPath);
            if (!buildDir.exists() && !buildDir.mkdirs()) {
                throw new IOException("Failed to create build directory: " + buildPath);
            }
            FunctionBuildContext context = FunctionBuildContext.builder()
                    .function(function)
                    .appPath(Paths.get(appPath))
                    .buildPath(Paths.get(buildPath))
                    .language(language)
                    .framework(framework)
                    .build();
            context.validate();
            createMainApplication(context);
            createFunctionWrapper(context);
            createDependencyFile(context);
            createDockerfile(context);
            createSupportFiles(context);
            copyEnvironmentFiles(context);

            return true;
        } catch (ValidationException | ResourceNotFoundException e) {
            throw e;
        } catch (IOException e) {
            logger.error("I/O error creating serverless function: {}", e.getMessage(), e);
            throw new CodeAnalysisException("Failed to create serverless function files: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error creating serverless function: {}", e.getMessage(), e);
            throw new CodeAnalysisException("Failed to create serverless function: " + e.getMessage(), e);
        }
    }

    /**
     * Create the main application file
     *
     * @param context Function build context
     * @return File content
     * @throws ValidationException If the context is invalid
     * @throws CodeAnalysisException If creating the file fails
     */
    public abstract String createMainApplication(FunctionBuildContext context);

    /**
     * Create the function wrapper file
     *
     * @param context Function build context
     * @return File content
     * @throws ValidationException If the context is invalid
     * @throws CodeAnalysisException If creating the file fails
     */
    public abstract String createFunctionWrapper(FunctionBuildContext context);

    /**
     * Create the dependency file (e.g., requirements.txt, pom.xml)
     *
     * @param context Function build context
     * @return File content
     * @throws ValidationException If the context is invalid
     * @throws CodeAnalysisException If creating the file fails
     */
    public abstract String createDependencyFile(FunctionBuildContext context);

    /**
     * Create the Dockerfile
     *
     * @param context Function build context
     * @return File content
     * @throws ValidationException If the context is invalid
     * @throws CodeAnalysisException If creating the file fails
     */
    public abstract String createDockerfile(FunctionBuildContext context);

    /**
     * Create additional support files if needed
     *
     * @param context Function build context
     * @throws ValidationException If the context is invalid
     * @throws IOException If creating the files fails
     * @throws CodeAnalysisException If creating the files fails
     */
    public abstract void createSupportFiles(FunctionBuildContext context) throws IOException;

    /**
     * Copy environment files from the original application to the build directory
     *
     * @param context Function build context
     * @throws IOException If copying files fails
     */
    protected void copyEnvironmentFiles(FunctionBuildContext context) throws IOException {
        Path envFile = context.getAppPath().resolve(".env");
        if (Files.exists(envFile)) {
            Files.copy(envFile, context.getBuildPath().resolve(".env"));
            logger.debug("Copied .env file to build directory");
        }
        for (String envVar : context.getFunction().getEnvVars()) {
            for (String configFilename : new String[]{
                    envVar.toLowerCase() + ".conf",
                    envVar.toLowerCase() + ".json",
                    envVar.toLowerCase() + ".yaml",
                    envVar.toLowerCase() + ".yml"
            }) {
                Path configPath = context.getAppPath().resolve(configFilename);
                if (Files.exists(configPath)) {
                    Files.copy(configPath, context.getBuildPath().resolve(configFilename));
                    logger.debug("Copied configuration file {} to build directory", configFilename);
                }
            }
        }
    }

    /**
     * Write a file to the build directory
     *
     * @param context Function build context
     * @param filename Filename
     * @param content File content
     * @throws IOException If writing fails
     */
    protected void writeFile(FunctionBuildContext context, String filename, String content) throws IOException {
        if (context == null) {
            throw new IllegalArgumentException("Build context cannot be null");
        }

        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        if (content == null) {
            content = "";
        }

        Path filePath = context.getBuildPath().resolve(filename);
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        logger.debug("Created file: {}", filePath);
    }
}