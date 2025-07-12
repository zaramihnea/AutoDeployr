package com.domain.services;

import com.domain.entities.User;
import com.domain.exceptions.BusinessRuleException;

import java.util.Set;

/**
 * Service interface for user management operations
 */
public interface IUserService {
    /**
     * Get a user by ID
     *
     * @param userId User ID
     * @return User entity
     * @throws BusinessRuleException If user not found
     */
    User getUserById(String userId);

    /**
     * Change a user's password
     *
     * @param userId User ID
     * @param currentPassword Current password
     * @param newPassword New password
     * @throws BusinessRuleException If passwords don't match or other validation issues
     */
    void changePassword(String userId, String currentPassword, String newPassword);

    /**
     * Update a user's roles
     *
     * @param userId User ID
     * @param roles New set of roles
     * @return Updated user
     * @throws BusinessRuleException If validation fails
     */
    User updateUserRoles(String userId, Set<String> roles);

    /**
     * Delete a user account
     *
     * @param userId User ID
     * @throws BusinessRuleException If user not found
     */
    void deleteAccount(String userId);
}