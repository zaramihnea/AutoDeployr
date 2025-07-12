package com.application.usecases.commandhandlers.user;

import com.application.dtos.response.user.UserResponse;
import com.application.exceptions.CommandException;
import com.application.usecases.commands.user.ChangeUserRoleCommand;
import com.domain.entities.User;
import com.domain.exceptions.BusinessRuleException;
import com.domain.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChangeUserRoleCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(ChangeUserRoleCommandHandler.class);

    private final IUserService userService;

    /**
     * Handle the change user role command
     *
     * @param command Command to handle
     * @return Updated user response
     * @throws CommandException If role change fails
     */
    public UserResponse handle(ChangeUserRoleCommand command) {
        try {
            command.validate();

            logger.info("Changing roles for user ID: {} to: {}", command.getUserId(), command.getRoles());

            // Update roles using domain service
            User updatedUser = userService.updateUserRoles(command.getUserId(), command.getRoles());

            logger.info("Roles updated successfully for user ID: {}", command.getUserId());

            // Map to response
            return UserResponse.builder()
                    .id(updatedUser.getId())
                    .username(updatedUser.getUsername())
                    .email(updatedUser.getEmail())
                    .firstName(updatedUser.getFirstName())
                    .lastName(updatedUser.getLastName())
                    .roles(updatedUser.getRoles())
                    .createdAt(updatedUser.getCreatedAt())
                    .build();
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Role change failed for user {}: {}", command.getUserId(), e.getMessage(), e);
            throw new CommandException("ChangeUserRole", "Role change failed: " + e.getMessage(), e);
        }
    }
}