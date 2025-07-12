package com.infrastructure.repositories;

import com.domain.entities.Function;
import com.domain.entities.FunctionBuildContext;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.exceptions.ValidationException;
import com.domain.repositories.IFunctionTransformerRepository;
import com.infrastructure.exceptions.CodeAnalysisException;
import com.infrastructure.exceptions.FileOperationException;
import com.infrastructure.exceptions.TemplateException;
// Înlocuim importul specific cu factory-ul
import com.infrastructure.services.transformer.AbstractFunctionTransformer;
import com.infrastructure.services.transformer.FunctionTransformerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implementation of IFunctionTransformerRepository.
 * Orchestrates the creation of serverless function build artifacts,
 * delegating language-specific file generation to transformers.
 */
@Repository
public class FunctionTransformerRepositoryImpl implements IFunctionTransformerRepository {
    private static final Logger logger = LoggerFactory.getLogger(FunctionTransformerRepositoryImpl.class);
    private final FunctionTransformerFactory transformerFactory;

    @Autowired
    public FunctionTransformerRepositoryImpl(FunctionTransformerFactory transformerFactory) {
        this.transformerFactory = transformerFactory;
    }

    @Override
    public boolean createServerlessFunction(Function function, String appPath, String buildPath) {
        try {
            if (function == null) throw new ValidationException("function", "Function cannot be null");
            if (appPath == null || appPath.isBlank()) throw new ValidationException("appPath", "Application path cannot be empty");
            if (buildPath == null || buildPath.isBlank()) throw new ValidationException("buildPath", "Build path cannot be empty");
            
            logger.info("Creating serverless function '{}' from app '{}' in build path '{}'",
                    function.getName(), appPath, buildPath);
            Path buildPathDir = Paths.get(buildPath);
            try {
                Files.createDirectories(buildPathDir);
            } catch (IOException e) {
                throw new FileOperationException("create", buildPath, "Failed to create build directory", e);
            }
            FunctionBuildContext context = FunctionBuildContext.builder()
                    .function(function)
                    .appPath(Paths.get(appPath))
                    .buildPath(buildPathDir)
                    .language(function.getLanguage() != null ? function.getLanguage() : "python")
                    .framework(function.getFramework() != null ? function.getFramework() : "flask")
                    .build();
            AbstractFunctionTransformer transformer;
            try {
                transformer = transformerFactory.createTransformer(context.getLanguage(), context.getFramework());
                logger.info("Using transformer for {}/{}: {}", 
                    context.getLanguage(), context.getFramework(), transformer.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to create transformer for {}/{}: {}", 
                    context.getLanguage(), context.getFramework(), e.getMessage());
                throw new CodeAnalysisException("Unsupported language/framework combination: " + 
                    context.getLanguage() + "/" + context.getFramework(), e);
            }
            transformer.createMainApplication(context);
            transformer.createFunctionWrapper(context);
            transformer.createDependencyFile(context);
            transformer.createDockerfile(context);
            transformer.createSupportFiles(context);
            Path sourceEnvFile = Paths.get(appPath, ".env");
            if (Files.exists(sourceEnvFile)) {
                Path targetEnvFile = buildPathDir.resolve(".env");
                try {
                    Files.copy(sourceEnvFile, targetEnvFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.debug("Copied .env file to build directory.");
                } catch (IOException e) {
                    logger.warn("Failed to copy .env file from {} to {}: {}", sourceEnvFile, targetEnvFile, e.getMessage());
                }
            }

            logger.info("Successfully created serverless function '{}' build artifacts in {}", function.getName(), buildPath);
            return true;

        } catch (ValidationException | ResourceNotFoundException e) {
            logger.error("Domain validation/resource error: {}", e.getMessage());
            throw e;
        } catch (FileOperationException | CodeAnalysisException | TemplateException e) {
            logger.error("Infrastructure error creating serverless function: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            String funcName = (function != null) ? function.getName() : "[unknown function]";
            logger.error("Unexpected error creating serverless function '{}': {}", funcName, e.getMessage(), e);
            throw new CodeAnalysisException("Unexpected failure during serverless function creation for " + funcName + ": " + e.getMessage(), e);
        }
    }
}