package com.application.usecases.queryhandlers.user;

import com.application.dtos.response.user.UserResponse;
import com.application.exceptions.QueryException;
import com.application.usecases.queries.user.GetUserDataQuery;
import com.domain.entities.User;
import com.domain.exceptions.BusinessRuleException;
import com.domain.services.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetUserDataQueryHandlerTest {

    @Mock
    private IUserService userService;

    @InjectMocks
    private GetUserDataQueryHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldReturnUserData() {
        // Arrange
        String userId = "user123";
        GetUserDataQuery query = new GetUserDataQuery(userId);

        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");

        LocalDateTime createdAt = LocalDateTime.now().minusDays(10);

        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .roles(roles)
                .createdAt(createdAt)
                .build();

        when(userService.getUserById(userId)).thenReturn(user);

        // Act
        UserResponse result = handler.handle(query);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
        assertEquals("Test", result.getFirstName());
        assertEquals("User", result.getLastName());
        assertEquals(roles, result.getRoles());
        assertEquals(createdAt, result.getCreatedAt());

        verify(userService).getUserById(userId);
    }

    @Test
    void shouldPassThroughDomainExceptions() {
        // Arrange
        String userId = "user123";
        GetUserDataQuery query = new GetUserDataQuery(userId);

        BusinessRuleException domainException = new BusinessRuleException("User not found");
        when(userService.getUserById(userId)).thenThrow(domainException);

        // Act & Assert
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> handler.handle(query));
        assertEquals("User not found", exception.getMessage());
        verify(userService).getUserById(userId);
    }

    @Test
    void shouldThrowQueryExceptionWhenServiceFails() {
        // Arrange
        String userId = "user123";
        GetUserDataQuery query = new GetUserDataQuery(userId);
        when(userService.getUserById(userId)).thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        QueryException exception = assertThrows(QueryException.class, () -> handler.handle(query));
        assertTrue(exception.getMessage().contains("Error retrieving user data"));
        verify(userService).getUserById(userId);
    }
}