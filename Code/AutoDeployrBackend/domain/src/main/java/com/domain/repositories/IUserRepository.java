package com.domain.repositories;

import com.domain.entities.User;
import com.domain.exceptions.ResourceNotFoundException;

import java.util.Optional;

/**
 * Repository interface for user operations
 */
public interface IUserRepository {
    /**
     * Find a user by ID
     *
     * @param id User ID
     * @return Optional containing the user if found
     */
    Optional<User> findById(String id);

    /**
     * Find a user by username
     *
     * @param username Username
     * @return Optional containing the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Save a user
     *
     * @param user User to save
     * @return Saved user
     */
    User save(User user);

    /**
     * Delete a user by ID
     *
     * @param id User ID
     * @throws ResourceNotFoundException If the user doesn't exist
     */
    void deleteById(String id);

    /**
     * Check if a username exists
     *
     * @param username Username to check
     * @return True if the username exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if an email exists
     *
     * @param email Email to check
     * @return True if the email exists
     */
    boolean existsByEmail(String email);
}