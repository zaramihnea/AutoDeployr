package com.application.dtos.response;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Response DTO for function execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionResponse {
    @Builder.Default
    private int statusCode = 200;

    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    private Object body;

    /**
     * Validate the response
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
        if (statusCode < 100 || statusCode >= 600) {
            throw new ValidationException("statusCode", "Invalid HTTP status code: " + statusCode);
        }
        if (headers == null) {
            headers = new HashMap<>();
        }
    }

    /**
     * Create a success response
     *
     * @param body Response body
     * @return Success response
     */
    public static FunctionResponse success(Object body) {
        return FunctionResponse.builder()
                .statusCode(200)
                .body(body)
                .build();
    }

    /**
     * Create a success response with custom status code
     *
     * @param statusCode HTTP status code
     * @param body Response body
     * @return Success response
     */
    public static FunctionResponse success(int statusCode, Object body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new ValidationException("statusCode",
                    "Success response must have status code between 200-299, got: " + statusCode);
        }

        return FunctionResponse.builder()
                .statusCode(statusCode)
                .body(body)
                .build();
    }

    /**
     * Create an error response
     *
     * @param statusCode HTTP status code
     * @param errorMessage Error message
     * @return Error response
     */
    public static FunctionResponse error(int statusCode, String errorMessage) {
        if (statusCode < 400 || statusCode >= 600) {
            throw new ValidationException("statusCode",
                    "Error response must have status code between 400-599, got: " + statusCode);
        }

        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new ValidationException("errorMessage", "Error message cannot be empty");
        }

        return FunctionResponse.builder()
                .statusCode(statusCode)
                .body(Map.of("error", errorMessage))
                .build();
    }

}