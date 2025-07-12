package com.domain.repositories;

import com.domain.entities.Function;
import com.domain.exceptions.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for function operations
 */
public interface IFunctionRepository {
    /**
     * Find all functions
     *
     * @return List of all functions
     */
    List<Function> findAll();

    /**
     * Find a function by ID
     *
     * @param id Function ID
     * @return Optional containing the function if found
     */
    Optional<Function> findById(String id);

    /**
     * Find a function by name
     *
     * @param name Function name
     * @return Optional containing the function if found
     */
    Optional<Function> findByName(String name);

    /**
     * Find a function by application name and function name
     *
     * @param appName Application name
     * @param functionName Function name
     * @return Optional containing the function if found
     */
    Optional<Function> findByAppNameAndName(String appName, String functionName);

    /**
     * Find a function by application name, function name, and user ID
     *
     * @param appName Application name
     * @param functionName Function name
     * @param userId User ID
     * @return Optional containing the function if found
     */
    Optional<Function> findByAppNameAndNameAndUserId(String appName, String functionName, String userId);

    /**
     * Find all functions owned by a user
     *
     * @param userId User ID
     * @return List of functions
     */
    List<Function> findByUserId(String userId);

    /**
     * Save a function
     *
     * @param function Function to save
     * @return Saved function
     */
    Function save(Function function);

    /**
     * Delete a function by name
     *
     * @param name Function name
     * @throws ResourceNotFoundException If the function doesn't exist
     */
    void deleteByName(String name);

    /**
     * Delete a specific function
     *
     * @param function Function to delete
     */
    void delete(Function function);

    /**
     * Find a function by name and application name
     *
     * @param name Function name
     * @param appName Application name
     * @return Function if found
     */
    Function findByNameAndAppName(String name, String appName);

    /**
     * Find all functions by application name
     *
     * @param appName Application name
     * @return List of functions for the given application
     */
    List<Function> findByAppName(String appName);
    
    /**
     * Count the number of functions for a specific application
     *
     * @param appName Application name
     * @return Number of functions for the given application
     */
    int countByAppName(String appName);

    /**
     * Count functions by application name and user ID
     *
     * @param appName Application name
     * @param userId User ID
     * @return Function count
     */
    int countByAppNameAndUserId(String appName, String userId);

    /**
     * Find a function by name and user ID
     *
     * @param name Function name
     * @param userId User ID
     * @return Optional containing the function if found
     */
    Optional<Function> findByNameAndUserId(String name, String userId);

    /**
     * Find a function by name with user preference
     * This method prioritizes user-scoped lookups over global lookups
     *
     * @param name Function name
     * @param preferredUserId Preferred user ID (optional)
     * @return Optional containing the function if found
     */
    Optional<Function> findByNameWithUserPreference(String name, String preferredUserId);

    /**
     * Find a function by its API key
     *
     * @param apiKey API key
     * @return Optional containing the function if found
     */
    Optional<Function> findByApiKey(String apiKey);

    /**
     * Find all private or public functions for a user
     *
     * @param userId User ID
     * @param isPrivate Whether to find private (true) or public (false) functions
     * @return List of functions
     */
    List<Function> findByUserIdAndIsPrivate(String userId, boolean isPrivate);

    /**
     * Find all private or public functions
     *
     * @param isPrivate Whether to find private (true) or public (false) functions
     * @return List of functions
     */
    List<Function> findByIsPrivate(boolean isPrivate);
}