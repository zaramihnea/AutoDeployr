package com.application.dtos.response;

import com.domain.exceptions.ValidationException;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Error response model for API errors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response structure")
public class ErrorResponse {

    @Schema(description = "Error code for programmatic identification")
    private String code;

    @Schema(description = "HTTP status code")
    private int status;

    @Schema(description = "Human-readable error message")
    private String message;

    @Schema(description = "Technical details for debugging (not shown in production)")
    private String details;

    @Schema(description = "Link to documentation for this error")
    private String moreInfo;

    /**
     * Create a simple error response with just a message
     *
     * @param message Error message
     * @return New error response
     */
    public static ErrorResponse of(String message) {
        return ErrorResponse.builder()
                .message(message)
                .build();
    }

    /**
     * Create an error response with code, status and message
     *
     * @param code Error code
     * @param status HTTP status
     * @param message Error message
     * @return New error response
     */
    public static ErrorResponse of(String code, int status, String message) {
        return ErrorResponse.builder()
                .code(code)
                .status(status)
                .message(message)
                .build();
    }

    /**
     * Create a detailed error response
     *
     * @param code Error code
     * @param status HTTP status
     * @param message Error message
     * @param details Technical details
     * @return New error response
     */
    public static ErrorResponse of(String code, int status, String message, String details) {
        return ErrorResponse.builder()
                .code(code)
                .status(status)
                .message(message)
                .details(details)
                .build();
    }

    /**
     * Validate the error response
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
        if (message == null || message.trim().isEmpty()) {
            throw new ValidationException("message", "Error message cannot be empty");
        }

        if (status < 100 || status >= 600) {
            throw new ValidationException("status", "Invalid HTTP status code: " + status);
        }
    }

    /**
     * Create an error response from a BaseException
     *
     * @param exception The exception
     * @return Error response
     */
    public static ErrorResponse fromException(com.domain.exceptions.BaseException exception) {
        return ErrorResponse.builder()
                .code(exception.getErrorCode())
                .status(exception.getStatusCode())
                .message(exception.getMessage())
                .build();
    }

    /**
     * Create an error response from any exception
     *
     * @param exception The exception
     * @return Error response
     */
    public static ErrorResponse fromException(Exception exception) {
        if (exception instanceof com.domain.exceptions.BaseException) {
            return fromException((com.domain.exceptions.BaseException) exception);
        }
        return ErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .status(500)
                .message(exception.getMessage())
                .build();
    }
}