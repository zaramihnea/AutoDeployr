package com.application.usecases.commandhandlers.auth;

import com.application.exceptions.CommandException;
import com.application.usecases.commands.auth.SignupCommand;
import com.domain.entities.User;
import com.domain.entities.UserRole;
import com.domain.exceptions.BusinessRuleException;
import com.domain.repositories.IUserRepository;
import com.domain.services.IPasswordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SignupCommandHandlerTest {

    @Mock
    private IUserRepository userRepository;

    @Mock
    private IPasswordService passwordEncoder;

    @InjectMocks
    private SignupCommandHandler handler;

    private final String username = "testuser";
    private final String email = "test@example.com";
    private final String password = "password123";
    private final String encodedPassword = "encoded_password";
    private final String firstName = "Test";
    private final String lastName = "User";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateUserSuccessfully() {
        // Arrange
        SignupCommand command = new SignupCommand(username, email, password, firstName, lastName);

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);

        User savedUser = User.builder()
                .id("user123")
                .username(username)
                .email(email)
                .password(encodedPassword)
                .firstName(firstName)
                .lastName(lastName)
                .build();
        savedUser.addRole(UserRole.ROLE_USER.toString());

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        String result = handler.handle(command);

        // Assert
        assertNotNull(result);
        assertEquals("user123", result);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User capturedUser = userCaptor.getValue();
        assertNotNull(capturedUser.getId());
        assertEquals(username, capturedUser.getUsername());
        assertEquals(email, capturedUser.getEmail());
        assertEquals(encodedPassword, capturedUser.getPassword());
        assertEquals(firstName, capturedUser.getFirstName());
        assertEquals(lastName, capturedUser.getLastName());
        assertTrue(capturedUser.getRoles().contains(UserRole.ROLE_USER.toString()));

        verify(userRepository).existsByUsername(username);
        verify(userRepository).existsByEmail(email);
        verify(passwordEncoder).encode(password);
    }

    @Test
    void shouldThrowExceptionWhenUsernameExists() {
        // Arrange
        SignupCommand command = new SignupCommand(username, email, password, firstName, lastName);

        when(userRepository.existsByUsername(username)).thenReturn(true);

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> handler.handle(command));
        assertEquals("Username already exists", exception.getMessage());

        verify(userRepository).existsByUsername(username);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldThrowExceptionWhenEmailExists() {
        // Arrange
        SignupCommand command = new SignupCommand(username, email, password, firstName, lastName);

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(true);

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> handler.handle(command));
        assertEquals("Email already exists", exception.getMessage());

        verify(userRepository).existsByUsername(username);
        verify(userRepository).existsByEmail(email);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldThrowCommandExceptionWhenRepositoryFails() {
        // Arrange
        SignupCommand command = new SignupCommand(username, email, password, firstName, lastName);

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CommandException exception = assertThrows(CommandException.class, () -> handler.handle(command));
        assertTrue(exception.getMessage().contains("Signup failed"));

        verify(userRepository).existsByUsername(username);
        verify(userRepository).existsByEmail(email);
        verify(passwordEncoder).encode(password);
        verify(userRepository).save(any(User.class));
    }
}