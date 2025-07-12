package com.application.usecases.queryhandlers.function;

import com.application.dtos.response.FunctionSummaryResponse;
import com.application.exceptions.QueryException;
import com.application.usecases.queries.function.GetUserFunctionsQuery;
import com.domain.entities.Function;
import com.domain.repositories.IFunctionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetUserFunctionsQueryHandlerTest {

    @Mock
    private IFunctionRepository functionRepository;

    @InjectMocks
    private GetUserFunctionsQueryHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldReturnEmptyListWhenNoFunctionsFound() {
        // Arrange
        String userId = "user123";
        GetUserFunctionsQuery query = new GetUserFunctionsQuery(userId);
        when(functionRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // Act
        List<FunctionSummaryResponse> result = handler.handle(query);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(functionRepository).findByUserId(userId);
    }

    @Test
    void shouldMapFunctionsToSummaryResponses() {
        // Arrange
        String userId = "user123";
        GetUserFunctionsQuery query = new GetUserFunctionsQuery(userId);

        Function function1 = Function.builder()
                .id("func1")
                .name("testFunction1")
                .path("/test1")
                .methods(Arrays.asList("GET", "POST"))
                .appName("testApp")
                .language("python")
                .framework("flask")
                .invocationCount(10L)
                .totalExecutionTimeMs(150)
                .lastInvoked(LocalDateTime.now())
                .build();

        Function function2 = Function.builder()
                .id("func2")
                .name("testFunction2")
                .path("/test2")
                .methods(Collections.singletonList("GET"))
                .appName("testApp")
                .language("java")
                .framework("spring")
                .invocationCount(5L)
                .totalExecutionTimeMs(100)
                .lastInvoked(LocalDateTime.now())
                .build();

        when(functionRepository.findByUserId(userId)).thenReturn(Arrays.asList(function1, function2));

        // Act
        List<FunctionSummaryResponse> result = handler.handle(query);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals("func1", result.get(0).getId());
        assertEquals("testFunction1", result.get(0).getName());
        assertEquals("/test1", result.get(0).getPath());
        assertEquals(Arrays.asList("GET", "POST"), result.get(0).getMethods());
        assertEquals("testApp", result.get(0).getAppName());
        assertEquals("python", result.get(0).getLanguage());
        assertEquals("flask", result.get(0).getFramework());
        assertEquals(10L, result.get(0).getInvocationCount());
        assertEquals(150.0, result.get(0).getAverageExecutionTimeMs());
        assertNotNull(result.get(0).getLastInvoked());

        assertEquals("func2", result.get(1).getId());
        assertEquals("java", result.get(1).getLanguage());
        assertEquals("spring", result.get(1).getFramework());

        verify(functionRepository).findByUserId(userId);
    }

    @Test
    void shouldThrowQueryExceptionWhenRepositoryFails() {
        // Arrange
        String userId = "user123";
        GetUserFunctionsQuery query = new GetUserFunctionsQuery(userId);
        when(functionRepository.findByUserId(userId)).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        QueryException exception = assertThrows(QueryException.class, () -> handler.handle(query));
        assertTrue(exception.getMessage().contains("Error retrieving user functions"));
        verify(functionRepository).findByUserId(userId);
    }
}