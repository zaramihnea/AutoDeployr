package com.application.dtos.response;

import com.domain.exceptions.ValidationException;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Generic API response wrapper for consistent response format
 *
 * @param <T> Type of data in the response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response format")
public class ApiResponse<T> {

    @Schema(description = "Response status - true for success, false for failure")
    @Builder.Default
    private boolean success = true;

    @Schema(description = "Human-readable message describing the result")
    private String message;

    @Schema(description = "Actual response data")
    private T data;

    @Schema(description = "Error details in case of failure")
    private ErrorResponse error;

    @Schema(description = "Response timestamp")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Validate the response
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
        if (error != null && success) {
            throw new ValidationException("success", "Success must be false when error is present");
        }
        if (error != null) {
            error.validate();
        }
    }

    /**
     * Create a success response
     *
     * @param <R> Type of data
     * @param data Response data
     * @param message Success message
     * @return Success API response
     */
    public static <R> ApiResponse<R> success(R data, String message) {
        return ApiResponse.<R>builder()
                .success(true)
                .data(data)
                .message(message)
                .build();
    }

    /**
     * Create an error response
     *
     * @param <R> Type of data
     * @param error Error response
     * @param message Error message
     * @return Error API response
     */
    public static <R> ApiResponse<R> error(ErrorResponse error, String message) {
        return ApiResponse.<R>builder()
                .success(false)
                .error(error)
                .message(message)
                .build();
    }
}