package com.infrastructure.persistence.repository;

import com.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringUserRepository extends JpaRepository<UserEntity, String> {
    /**
     * Find a user by username
     *
     * @param username Username
     * @return Optional containing the user if found
     */
    Optional<UserEntity> findByUsername(String username);

    /**
     * Check if a user with the given username exists
     *
     * @param username Username
     * @return True if a user with the username exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if a user with the given email exists
     *
     * @param email Email
     * @return True if a user with the email exists
     */
    boolean existsByEmail(String email);
}