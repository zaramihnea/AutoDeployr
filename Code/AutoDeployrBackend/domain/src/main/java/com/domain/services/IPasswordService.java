package com.domain.services;

/**
 * Service interface for password operations
 */
public interface IPasswordService {
    /**
     * Encode a password
     *
     * @param rawPassword Raw password
     * @return Encoded password
     */
    String encode(String rawPassword);

    /**
     * Check if a raw password matches an encoded password
     *
     * @param rawPassword Raw password
     * @param encodedPassword Encoded password
     * @return true if the password matches
     */
    boolean matches(String rawPassword, String encodedPassword);
}