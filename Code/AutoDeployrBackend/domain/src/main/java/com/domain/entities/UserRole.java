package com.domain.entities;

/**
 * Enum for user roles
 */
public enum UserRole {
    ROLE_USER,
    ROLE_ADMIN;

    @Override
    public String toString() {
        return name();
    }
}