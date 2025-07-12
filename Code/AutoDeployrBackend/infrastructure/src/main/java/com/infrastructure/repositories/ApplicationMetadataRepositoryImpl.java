package com.infrastructure.repositories;

import com.domain.entities.ApplicationMetadata;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.repositories.IApplicationMetadataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infrastructure.exceptions.FileOperationException;
import com.infrastructure.exceptions.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Implementation of IApplicationMetadataRepository
 * Note: Environment variables are NOT stored here for security reasons.
 * Use EnvironmentVariableService for secure, encrypted environment variable storage.
 */
@Repository
public class ApplicationMetadataRepositoryImpl implements IApplicationMetadataRepository {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationMetadataRepositoryImpl.class);
    private static final String METADATA_FILENAME = "app-metadata.json";

    private final ObjectMapper objectMapper;

    public ApplicationMetadataRepositoryImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ApplicationMetadata createMetadata(
            String appName,
            String appPath,
            String buildPath) {

        try {
            ApplicationMetadata metadata = ApplicationMetadata.builder()
                    .appName(appName)
                    .appPath(appPath)
                    .deployedAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            Path buildDirPath = Paths.get(buildPath);
            if (!Files.exists(buildDirPath)) {
                try {
                    Files.createDirectories(buildDirPath);
                } catch (IOException e) {
                    logger.error("Failed to create build directory: {}", e.getMessage(), e);
                    throw new FileOperationException("create", buildPath, "Failed to create build directory: " + e.getMessage(), e);
                }
            }
            Path metadataPath = buildDirPath.resolve(METADATA_FILENAME);
            objectMapper.writeValue(metadataPath.toFile(), metadata);

            logger.info("Created application metadata for {} at {}", appName, metadataPath);
            return metadata;

        } catch (IOException e) {
            logger.error("Error creating metadata file: {}", e.getMessage(), e);
            throw new FileOperationException("write", buildPath + "/" + METADATA_FILENAME,
                    "Failed to create metadata file: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error creating metadata: {}", e.getMessage(), e);
            throw new StorageException("Failed to create application metadata: " + e.getMessage(), e);
        }
    }

    @Override
    public ApplicationMetadata readMetadata(String buildPath) {
        Path metadataPath = Paths.get(buildPath, METADATA_FILENAME);

        if (!Files.exists(metadataPath)) {
            logger.warn("Metadata file not found at {}", metadataPath);
            throw new ResourceNotFoundException("Application metadata", buildPath);
        }

        try {
            return objectMapper.readValue(metadataPath.toFile(), ApplicationMetadata.class);
        } catch (IOException e) {
            logger.error("Error reading metadata file: {}", e.getMessage(), e);
            throw new FileOperationException("read", metadataPath.toString(),
                    "Failed to read metadata file: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error reading metadata: {}", e.getMessage(), e);
            throw new StorageException("Failed to read application metadata: " + e.getMessage(), e);
        }
    }

    @Override
    public ApplicationMetadata updateMetadata(String buildPath, Function<ApplicationMetadata, ApplicationMetadata> updater) {
        Path metadataPath = Paths.get(buildPath, METADATA_FILENAME);
        if (!Files.exists(metadataPath)) {
            logger.warn("Cannot update metadata: file not found at {}", metadataPath);
            throw new ResourceNotFoundException("Application metadata", buildPath);
        }

        try {
            ApplicationMetadata metadata = objectMapper.readValue(metadataPath.toFile(), ApplicationMetadata.class);
            metadata = updater.apply(metadata);
            metadata.setUpdatedAt(LocalDateTime.now());
            objectMapper.writeValue(metadataPath.toFile(), metadata);

            logger.info("Updated application metadata at {}", metadataPath);
            return metadata;

        } catch (IOException e) {
            logger.error("Error updating metadata file: {}", e.getMessage(), e);
            throw new FileOperationException("update", buildPath + "/" + METADATA_FILENAME,
                    "Failed to update metadata file: " + e.getMessage(), e);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error updating metadata: {}", e.getMessage(), e);
            throw new StorageException("Failed to update application metadata: " + e.getMessage(), e);
        }
    }

    @Override
    public ApplicationMetadata addDeployedFunction(String buildPath, String functionName) {
        try {
            return updateMetadata(buildPath, metadata -> {
                metadata.getDeployedFunctions().add(functionName);
                return metadata;
            });
        } catch (ResourceNotFoundException e) {
            logger.info("Creating new metadata for function: {}", functionName);
            ApplicationMetadata newMetadata = ApplicationMetadata.builder()
                    .appName(Paths.get(buildPath).getFileName().toString())
                    .appPath("")
                    .build();
            newMetadata.getDeployedFunctions().add(functionName);

            try {
                Path metadataPath = Paths.get(buildPath, METADATA_FILENAME);
                objectMapper.writeValue(metadataPath.toFile(), newMetadata);
                return newMetadata;
            } catch (IOException ioEx) {
                throw new FileOperationException("create", buildPath + "/" + METADATA_FILENAME,
                        "Failed to create new metadata file: " + ioEx.getMessage(), ioEx);
            }
        }
    }

    @Override
    public ApplicationMetadata removeDeployedFunction(String buildPath, String functionName) {
        try {
            return updateMetadata(buildPath, metadata -> {
                metadata.getDeployedFunctions().remove(functionName);
                return metadata;
            });
        } catch (ResourceNotFoundException e) {
            logger.warn("No metadata found to remove function: {}", functionName);
            throw e;
        }
    }
}