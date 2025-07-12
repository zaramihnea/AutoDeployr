package com.application.usecases.commandhandlers.auth;

import com.application.exceptions.CommandException;
import com.application.usecases.commands.auth.SignupCommand;
import com.domain.entities.User;
import com.domain.entities.UserRole;
import com.domain.exceptions.BusinessRuleException;
import com.domain.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SignupCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(SignupCommandHandler.class);

    private final IUserRepository userRepository;
    private final com.domain.services.IPasswordService passwordEncoder; // New domain service interface

    /**
     * Handle the signup command
     *
     * @param command Command to handle
     * @return Created user ID
     * @throws CommandException If signup fails
     */
    public String handle(SignupCommand command) {
        try {
            command.validate();

            logger.info("Creating user: {}", command.getUsername());

            // Check if username exists
            if (userRepository.existsByUsername(command.getUsername())) {
                throw new BusinessRuleException("Username already exists");
            }

            // Check if email exists
            if (userRepository.existsByEmail(command.getEmail())) {
                throw new BusinessRuleException("Email already exists");
            }

            // Create user
            User user = User.builder()
                    .id(UUID.randomUUID().toString())
                    .username(command.getUsername())
                    .email(command.getEmail())
                    .password(passwordEncoder.encode(command.getPassword()))
                    .firstName(command.getFirstName())
                    .lastName(command.getLastName())
                    .build();

            // Add default role
            user.addRole(UserRole.ROLE_USER.toString());

            // Save user
            User savedUser = userRepository.save(user);

            logger.info("User created successfully: {}", savedUser.getUsername());

            return savedUser.getId();
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Signup failed for user {}: {}", command.getUsername(), e.getMessage(), e);
            throw new CommandException("Signup", "Signup failed: " + e.getMessage(), e);
        }
    }
}