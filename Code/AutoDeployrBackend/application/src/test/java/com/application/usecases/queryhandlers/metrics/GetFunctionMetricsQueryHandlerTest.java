package com.application.usecases.queryhandlers.metrics;

import com.application.dtos.response.metrics.FunctionMetricsResponse;
import com.application.exceptions.QueryException;
import com.application.usecases.queries.metrics.GetFunctionMetricsQuery;
import com.domain.entities.FunctionMetrics;
import com.domain.services.IMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetFunctionMetricsQueryHandlerTest {

    @Mock
    private IMetricsService metricsService;

    @InjectMocks
    private GetFunctionMetricsQueryHandler handler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldReturnMetricsForFunction() {
        // Arrange
        String functionId = "func123";
        String userId = "user123";
        GetFunctionMetricsQuery query = new GetFunctionMetricsQuery(functionId, userId);

        LocalDateTime now = LocalDateTime.now();
        FunctionMetrics metrics = FunctionMetrics.builder()
                .functionId(functionId)
                .functionName("testFunction")
                .appName("testApp")
                .invocationCount(100L)
                .successCount(95L)
                .failureCount(5L)
                .successCount(10)
                .totalExecutionTimeMs(15000)
                .minExecutionTimeMs(50)
                .maxExecutionTimeMs(500)
                .lastInvoked(now)
                .build();

        when(metricsService.getFunctionMetrics(functionId)).thenReturn(metrics);

        // Act
        FunctionMetricsResponse result = handler.handle(query);

        // Assert
        assertNotNull(result);
        assertEquals(functionId, result.getFunctionId());
        assertEquals("testFunction", result.getFunctionName());
        assertEquals("testApp", result.getAppName());
        assertEquals(100L, result.getInvocationCount());
        assertEquals(95L, result.getSuccessCount());
        assertEquals(5L, result.getFailureCount());
        assertEquals(0.95, result.getSuccessRate());
        assertEquals(15000.0, result.getTotalExecutionTimeMs());
        assertEquals(15.0, result.getAverageExecutionTimeMs());
        assertEquals(50.0, result.getMinExecutionTimeMs());
        assertEquals(500.0, result.getMaxExecutionTimeMs());
        assertEquals(now, result.getLastInvoked());

        verify(metricsService).getFunctionMetrics(functionId);
    }

    @Test
    void shouldThrowQueryExceptionWhenServiceFails() {
        // Arrange
        String functionId = "func123";
        String userId = "user123";
        GetFunctionMetricsQuery query = new GetFunctionMetricsQuery(functionId, userId);
        when(metricsService.getFunctionMetrics(functionId)).thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        QueryException exception = assertThrows(QueryException.class, () -> handler.handle(query));
        assertTrue(exception.getMessage().contains("Error retrieving function metrics"));
        verify(metricsService).getFunctionMetrics(functionId);
    }
}