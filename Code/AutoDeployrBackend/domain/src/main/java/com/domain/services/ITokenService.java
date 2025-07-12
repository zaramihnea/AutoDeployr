package com.domain.services;

import java.util.List;

/**
 * Service interface for JWT token operations
 */
public interface ITokenService {
    /**
     * Generate a JWT token for a user
     *
     * @param userId User ID
     * @param username Username
     * @param roles User roles
     * @return JWT token
     */
    String generateToken(String userId, String username, List<String> roles);

    /**
     * Get username from JWT token
     *
     * @param token JWT token
     * @return Username
     */
    String getUsernameFromToken(String token);

    /**
     * Validate JWT token
     *
     * @param token JWT token
     * @return true if token is valid
     */
    boolean validateToken(String token);

    /**
     * Get token expiration time in milliseconds
     *
     * @return Expiration time
     */
    long getExpirationTime();
}