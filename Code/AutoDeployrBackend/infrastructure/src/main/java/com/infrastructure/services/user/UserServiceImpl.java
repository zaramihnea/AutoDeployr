package com.infrastructure.services.user;

import com.domain.entities.User;
import com.domain.exceptions.BusinessRuleException;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.repositories.IUserRepository;
import com.domain.services.IPasswordService;
import com.domain.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final IUserRepository userRepository;
    private final IPasswordService passwordService;

    @Override
    @Transactional(readOnly = true)
    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Override
    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = getUserById(userId);

        // Verify current password
        if (!passwordService.matches(currentPassword, user.getPassword())) {
            throw new BusinessRuleException("Current password is incorrect");
        }

        // Update password
        user.setPassword(passwordService.encode(newPassword));
        userRepository.save(user);

        logger.info("Password changed successfully for user: {}", user.getUsername());
    }

    @Override
    @Transactional
    public User updateUserRoles(String userId, Set<String> roles) {
        User user = getUserById(userId);
        for (String role : roles) {
            if (!role.startsWith("ROLE_")) {
                throw new BusinessRuleException("Invalid role format: " + role);
            }
        }
        user.setRoles(roles);
        User updatedUser = userRepository.save(user);

        logger.info("Roles updated for user {}: {}", user.getUsername(), roles);
        return updatedUser;
    }

    @Override
    @Transactional
    public void deleteAccount(String userId) {
        // Check if user exists
        if (!userRepository.findById(userId).isPresent()) {
            throw new ResourceNotFoundException("User", userId);
        }

        // Delete user
        userRepository.deleteById(userId);

        logger.info("User account deleted: {}", userId);
    }
}