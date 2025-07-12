package com.infrastructure.persistence.repository;

import com.infrastructure.persistence.entity.EnvironmentVariableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for environment variable entities
 */
@Repository
public interface SpringEnvironmentVariableRepository extends JpaRepository<EnvironmentVariableEntity, String> {

    /**
     * Find all environment variables for an application
     *
     * @param appName Application name
     * @param userId User ID
     * @return List of environment variables
     */
    List<EnvironmentVariableEntity> findByAppNameAndUserId(String appName, String userId);

    /**
     * Delete all environment variables for an application
     *
     * @param appName Application name
     * @param userId User ID
     */
    void deleteByAppNameAndUserId(String appName, String userId);
}