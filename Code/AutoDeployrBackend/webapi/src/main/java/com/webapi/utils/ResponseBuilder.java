package com.webapi.utils;

import com.application.dtos.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Utility class for building standardized API responses
 */
public class ResponseBuilder {

    private ResponseBuilder() {
    }

    /**
     * Build a success response with data
     *
     * @param message Success message
     * @param data Response data
     * @param <T> Type of data
     * @return ResponseEntity with ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> success(String message, T data) {
        return ResponseEntity.ok(
                ApiResponse.<T>builder()
                        .success(true)
                        .message(message)
                        .data(data)
                        .build()
        );
    }

    /**
     * Build a success response without data
     *
     * @param message Success message
     * @return ResponseEntity with ApiResponse
     */
    public static ResponseEntity<ApiResponse<Void>> success(String message) {
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message(message)
                        .build()
        );
    }

    /**
     * Build an error response
     *
     * @param message Error message
     * @param status HTTP status
     * @param <T> Type of data
     * @return ResponseEntity with ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> error(String message, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(ApiResponse.<T>builder()
                        .success(false)
                        .message(message)
                        .build()
                );
    }

    /**
     * Build an error response with specific data
     *
     * @param message Error message
     * @param status HTTP status
     * @param data Error data
     * @param <T> Type of data
     * @return ResponseEntity with ApiResponse
     */
    public static <T> ResponseEntity<ApiResponse<T>> error(String message, HttpStatus status, T data) {
        return ResponseEntity.status(status)
                .body(ApiResponse.<T>builder()
                        .success(false)
                        .message(message)
                        .data(data)
                        .build()
                );
    }
} 