package com.application.usecases.commandhandlers.user;

import com.application.exceptions.CommandException;
import com.application.usecases.commands.user.DeleteAccountCommand;
import com.domain.exceptions.BusinessRuleException;
import com.domain.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeleteAccountCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(DeleteAccountCommandHandler.class);

    private final IUserService userService;

    /**
     * Handle the delete account command
     *
     * @param command Command to handle
     * @throws CommandException If account deletion fails
     */
    public void handle(DeleteAccountCommand command) {
        try {
            command.validate();

            logger.info("Deleting account for user ID: {}", command.getUserId());

            // Delete account using domain service
            userService.deleteAccount(command.getUserId());

            logger.info("Account deleted successfully for user ID: {}", command.getUserId());
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Account deletion failed for user {}: {}", command.getUserId(), e.getMessage(), e);
            throw new CommandException("DeleteAccount", "Account deletion failed: " + e.getMessage(), e);
        }
    }
}