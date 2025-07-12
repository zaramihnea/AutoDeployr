package com.application.usecases.commandhandlers.user;

import com.application.exceptions.CommandException;
import com.application.usecases.commands.user.ResetPasswordCommand;
import com.domain.exceptions.BusinessRuleException;
import com.domain.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResetPasswordCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(ResetPasswordCommandHandler.class);

    private final IUserService userService;

    /**
     * Handle the reset password command
     *
     * @param command Command to handle
     * @throws CommandException If password reset fails
     */
    public void handle(ResetPasswordCommand command) {
        try {
            command.validate();

            logger.info("Resetting password for user ID: {}", command.getUserId());

            // Change password using domain service
            userService.changePassword(
                    command.getUserId(),
                    command.getCurrentPassword(),
                    command.getNewPassword()
            );

            logger.info("Password reset successful for user ID: {}", command.getUserId());
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Password reset failed for user {}: {}", command.getUserId(), e.getMessage(), e);
            throw new CommandException("ResetPassword", "Password reset failed: " + e.getMessage(), e);
        }
    }
}