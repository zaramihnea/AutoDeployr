package com.infrastructure.repositories;

import com.domain.entities.User;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.repositories.IUserRepository;
import com.infrastructure.exceptions.PersistenceException;
import com.infrastructure.persistence.entity.UserEntity;
import com.infrastructure.persistence.repository.SpringUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements IUserRepository {
    private static final Logger logger = LoggerFactory.getLogger(UserRepositoryImpl.class);

    private final SpringUserRepository springUserRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(String id) {
        try {
            return springUserRepository.findById(id)
                    .map(this::mapToUser);
        } catch (Exception e) {
            logger.error("Error finding user by ID {}: {}", id, e.getMessage(), e);
            throw new PersistenceException("Error finding user by ID", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        try {
            return springUserRepository.findByUsername(username)
                    .map(this::mapToUser);
        } catch (Exception e) {
            logger.error("Error finding user by username {}: {}", username, e.getMessage(), e);
            throw new PersistenceException("Error finding user by username", e);
        }
    }

    @Override
    @Transactional
    public User save(User user) {
        try {
            UserEntity entity = mapToEntity(user);
            UserEntity savedEntity = springUserRepository.save(entity);
            return mapToUser(savedEntity);
        } catch (Exception e) {
            logger.error("Error saving user {}: {}", user.getUsername(), e.getMessage(), e);
            throw new PersistenceException("Error saving user", e);
        }
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        try {
            if (!springUserRepository.existsById(id)) {
                throw new ResourceNotFoundException("User", id);
            }
            springUserRepository.deleteById(id);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error deleting user with ID {}: {}", id, e.getMessage(), e);
            throw new PersistenceException("Error deleting user", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        try {
            return springUserRepository.existsByUsername(username);
        } catch (Exception e) {
            logger.error("Error checking if username exists {}: {}", username, e.getMessage(), e);
            throw new PersistenceException("Error checking if username exists", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        try {
            return springUserRepository.existsByEmail(email);
        } catch (Exception e) {
            logger.error("Error checking if email exists {}: {}", email, e.getMessage(), e);
            throw new PersistenceException("Error checking if email exists", e);
        }
    }

    /**
     * Map from Entity to Domain model
     */
    private User mapToUser(UserEntity entity) {
        return User.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .password(entity.getPassword())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .roles(entity.getRoles())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .active(entity.isActive())
                .build();
    }

    /**
     * Map from Domain model to Entity
     */
    private UserEntity mapToEntity(User user) {
        return UserEntity.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .password(user.getPassword())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .active(user.isActive())
                .build();
    }
}