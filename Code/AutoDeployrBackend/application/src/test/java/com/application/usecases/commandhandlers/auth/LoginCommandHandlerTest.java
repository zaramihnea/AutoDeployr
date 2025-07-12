package com.application.usecases.commandhandlers.auth;

import com.application.dtos.response.auth.TokenResponse;
import com.application.exceptions.CommandException;
import com.application.usecases.commands.auth.LoginCommand;
import com.domain.entities.User;
import com.domain.exceptions.BusinessRuleException;
import com.domain.services.IAuthenticationService;
import com.domain.services.ITokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginCommandHandlerTest {

    @Mock
    private IAuthenticationService authenticationService;

    @Mock
    private ITokenService tokenService;

    @InjectMocks
    private LoginCommandHandler handler;

    private final String username = "testuser";
    private final String password = "password123";
    private final String userId = "user123";
    private final String token = "jwt.token.here";
    private final long expirationTime = 3600;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldLoginSuccessfully() {
        // Arrange
        LoginCommand command = new LoginCommand(username, password);

        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");

        User user = User.builder()
                .id(userId)
                .username(username)
                .roles(roles)
                .build();

        when(authenticationService.authenticate(username, password)).thenReturn(user);
        when(tokenService.generateToken(eq(userId), eq(username), anyList())).thenReturn(token);
        when(tokenService.getExpirationTime()).thenReturn(expirationTime);

        // Act
        TokenResponse response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(token, response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(expirationTime, response.getExpiresIn());
        assertEquals(userId, response.getUserId());
        assertEquals(username, response.getUsername());

        verify(authenticationService).authenticate(username, password);
        verify(tokenService).generateToken(eq(userId), eq(username), anyList());
        verify(tokenService).getExpirationTime();
    }

    @Test
    void shouldPassThroughDomainExceptions() {
        // Arrange
        LoginCommand command = new LoginCommand(username, password);

        BusinessRuleException domainException = new BusinessRuleException("Invalid credentials");
        when(authenticationService.authenticate(username, password)).thenThrow(domainException);

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> handler.handle(command));
        assertEquals("Invalid credentials", exception.getMessage());
        verify(authenticationService).authenticate(username, password);
        verifyNoInteractions(tokenService);
    }

    @Test
    void shouldThrowCommandExceptionWhenTokenGenerationFails() {
        // Arrange
        LoginCommand command = new LoginCommand(username, password);

        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");

        User user = User.builder()
                .id(userId)
                .username(username)
                .roles(roles)
                .build();

        when(authenticationService.authenticate(username, password)).thenReturn(user);
        when(tokenService.generateToken(eq(userId), eq(username), anyList())).thenThrow(new RuntimeException("Token generation failed"));

        // Act & Assert
        CommandException exception = assertThrows(CommandException.class, () -> handler.handle(command));
        assertTrue(exception.getMessage().contains("Error executing command 'Login'"));
        assertTrue(exception.getMessage().contains("Login failed"));

        verify(authenticationService).authenticate(username, password);
        verify(tokenService).generateToken(eq(userId), eq(username), anyList());
    }
}