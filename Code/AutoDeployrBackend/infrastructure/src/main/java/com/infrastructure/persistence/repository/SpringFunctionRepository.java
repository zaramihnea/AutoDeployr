package com.infrastructure.persistence.repository;

import com.infrastructure.persistence.entity.FunctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link FunctionEntity}
 */
@Repository
public interface SpringFunctionRepository extends JpaRepository<FunctionEntity, String> {

    /**
     * Find a function by its name
     *
     * @param name Function name
     * @return The function or empty if not found
     */
    Optional<FunctionEntity> findByName(String name);

    /**
     * Find a function by its name and user ID
     *
     * @param name Function name
     * @param userId User ID
     * @return The function or empty if not found
     */
    Optional<FunctionEntity> findByNameAndUserId(String name, String userId);

    /**
     * Delete a function by its name
     *
     * @param name Function name
     */
    @Modifying
    @Transactional
    void deleteByName(String name);

    /**
     * Find all functions with a specific HTTP method
     *
     * @param method HTTP method (GET, POST, etc.)
     * @return List of matching functions
     */
    @Query("SELECT f FROM FunctionEntity f JOIN f.methods m WHERE m = :method")
    List<FunctionEntity> findByHttpMethod(@Param("method") String method);

    /**
     * Find all functions by user ID
     */
    List<FunctionEntity> findByUserId(String userId);


    /**
     * Find a function by application name and function name
     *
     * @param appName Application name
     * @param functionName Function name
     * @return The function or empty if not found
     */
    @Query("SELECT f FROM FunctionEntity f WHERE f.appName = :appName AND f.name = :functionName AND f.userId = :userId")
    Optional<FunctionEntity> findByAppNameAndNameAndUserId(@Param("appName") String appName, @Param("functionName") String functionName, @Param("userId") String userId);

    /**
     * Find a function by application name and function name
     * 
     * @param appName Application name
     * @param functionName Function name
     * @return List of matching functions
     */
    List<FunctionEntity> findByAppNameAndName(String appName, String functionName);

    /**
     * Find all functions by application name
     *
     * @param appName Application name
     * @return List of functions in the application
     */
    List<FunctionEntity> findByAppName(String appName);
    
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
     * Find a function by its API key
     *
     * @param apiKey API key
     * @return The function or empty if not found
     */
    Optional<FunctionEntity> findByApiKey(String apiKey);

    /**
     * Find all private functions for a user
     *
     * @param userId User ID
     * @param isPrivate Whether to find private (true) or public (false) functions
     * @return List of functions
     */
    List<FunctionEntity> findByUserIdAndIsPrivate(String userId, boolean isPrivate);

    /**
     * Find all private functions
     *
     * @param isPrivate Whether to find private (true) or public (false) functions
     * @return List of functions
     */
    List<FunctionEntity> findByIsPrivate(boolean isPrivate);
}