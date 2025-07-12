package com.domain.entities;

import com.domain.exceptions.BusinessRuleException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of a function execution
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FunctionExecutionResult {
    @Builder.Default
    private int statusCode = 200;

    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    private Object body;

    @Builder.Default
    private boolean success = true;

    private String errorMessage;

    /**
     * Create a successful execution result
     *
     * @param statusCode HTTP status code
     * @param headers HTTP headers
     * @param body Response body
     * @return Success result
     */
    public static FunctionExecutionResult success(int statusCode, Map<String, String> headers, Object body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new BusinessRuleException("functionExecution",
                    "Success result cannot have error status code: " + statusCode);
        }
        if (body instanceof String) {
            String bodyStr = ((String) body).trim();
            if ((bodyStr.startsWith("{") && bodyStr.endsWith("}")) || 
                (bodyStr.startsWith("[") && bodyStr.endsWith("]"))) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper = 
                        new com.fasterxml.jackson.databind.ObjectMapper();
                    body = objectMapper.readValue(bodyStr, Object.class);
                } catch (Exception e) {
                    // If parsing fails, keep the original string
                    // No need to log here as this is a fallback
                }
            }
        }

        return FunctionExecutionResult.builder()
                .statusCode(statusCode)
                .headers(headers != null ? headers : new HashMap<>())
                .body(body)
                .success(true)
                .build();
    }

    /**
     * Create an error execution result
     *
     * @param errorMessage Error message
     * @return Error result
     */
    public static FunctionExecutionResult error(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new BusinessRuleException("functionExecution",
                    "Error message cannot be empty");
        }

        return FunctionExecutionResult.builder()
                .statusCode(500)
                .success(false)
                .errorMessage(errorMessage)
                .body(Map.of("error", errorMessage))
                .build();
    }

    /**
     * Create an error execution result with custom status code
     *
     * @param statusCode HTTP status code
     * @param errorMessage Error message
     * @return Error result
     */
    public static FunctionExecutionResult error(int statusCode, String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new BusinessRuleException("functionExecution",
                    "Error message cannot be empty");
        }

        if (statusCode < 400) {
            throw new BusinessRuleException("functionExecution",
                    "Error result must have status code >= 400, got: " + statusCode);
        }

        return FunctionExecutionResult.builder()
                .statusCode(statusCode)
                .success(false)
                .errorMessage(errorMessage)
                .body(Map.of("error", errorMessage))
                .build();
    }

    /**
     * Validate the execution result
     *
     * @throws BusinessRuleException If the result is invalid
     */
    public void validate() {
        if (success && statusCode >= 400) {
            throw new BusinessRuleException("functionExecutionResult",
                    "A successful result cannot have an error status code");
        }

        if (!success && (errorMessage == null || errorMessage.trim().isEmpty())) {
            throw new BusinessRuleException("functionExecutionResult",
                    "An error result must have an error message");
        }
    }
}