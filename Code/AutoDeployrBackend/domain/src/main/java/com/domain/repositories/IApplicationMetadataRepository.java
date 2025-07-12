package com.domain.repositories;

import com.domain.entities.ApplicationMetadata;
import java.util.Map;
import java.util.function.Function;

/**
 * Repository interface for application metadata operations
 */
public interface IApplicationMetadataRepository {

    /**
     * Create metadata for a new application
     *
     * @param appName Application name
     * @param appPath Original application path
     * @param buildPath Path to the build directory
     * @return Created application metadata
     */
    ApplicationMetadata createMetadata(
            String appName,
            String appPath,
            String buildPath);

    /**
     * Read metadata for an existing application
     *
     * @param buildPath Path to the build directory
     * @return Application metadata, or null if not found
     */
    ApplicationMetadata readMetadata(String buildPath);

    /**
     * Update application metadata
     *
     * @param buildPath Path to the build directory
     * @param updater Function to update the metadata
     * @return Updated metadata
     */
    ApplicationMetadata updateMetadata(String buildPath, Function<ApplicationMetadata, ApplicationMetadata> updater);

    /**
     * Add a deployed function to the application metadata
     *
     * @param buildPath Build directory path
     * @param functionName Name of the deployed function
     * @return Updated metadata, or null if metadata doesn't exist
     */
    ApplicationMetadata addDeployedFunction(String buildPath, String functionName);

    /**
     * Remove a deployed function from the application metadata
     *
     * @param buildPath Build directory path
     * @param functionName Name of the function to remove
     * @return Updated metadata, or null if metadata doesn't exist
     */
    ApplicationMetadata removeDeployedFunction(String buildPath, String functionName);

}