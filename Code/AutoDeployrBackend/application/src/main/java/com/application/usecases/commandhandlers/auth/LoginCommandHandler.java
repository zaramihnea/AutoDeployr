package com.application.usecases.commandhandlers.auth;

import com.application.dtos.response.auth.TokenResponse;
import com.application.exceptions.CommandException;
import com.application.usecases.commands.auth.LoginCommand;
import com.domain.entities.User;
import com.domain.exceptions.BusinessRuleException;
import com.domain.services.IAuthenticationService;
import com.domain.services.ITokenService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class LoginCommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(LoginCommandHandler.class);

    private final IAuthenticationService authenticationService;
    private final ITokenService tokenService;

    /**
     * Handle the login command
     *
     * @param command Command to handle
     * @return Token response
     * @throws CommandException If login fails
     */
    public TokenResponse handle(LoginCommand command) {
        try {
            command.validate();

            logger.info("Authenticating user: {}", command.getUsername());

            // Authenticate user (using domain service)
            User user = authenticationService.authenticate(command.getUsername(), command.getPassword());

            // Generate JWT token (using domain service)
            String jwt = tokenService.generateToken(
                    user.getId(),
                    user.getUsername(),
                    new ArrayList<>(user.getRoles())
            );

            return TokenResponse.builder()
                    .token(jwt)
                    .tokenType("Bearer")
                    .expiresIn(tokenService.getExpirationTime())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .build();
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Login failed for user {}: {}", command.getUsername(), e.getMessage(), e);
            throw new CommandException("Login", "Login failed: " + e.getMessage(), e);
        }
    }
}