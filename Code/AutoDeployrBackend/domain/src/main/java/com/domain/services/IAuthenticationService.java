package com.domain.services;

import com.domain.entities.User;
import com.domain.exceptions.BusinessRuleException;

/**
 * Service interface for user authentication
 */
public interface IAuthenticationService {
    /**
     * Authenticate a user with username and password
     *
     * @param username Username
     * @param password Password
     * @return Authenticated user if successful
     * @throws BusinessRuleException If authentication fails
     */
    User authenticate(String username, String password);
}