package com.webapi.exception;

import com.application.dtos.response.ApiResponse;
import com.application.dtos.response.ErrorResponse;
import com.infrastructure.exceptions.FileUploadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for all controllers
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle APIException and its subclasses
     */
    @ExceptionHandler(APIException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(APIException ex) {
        logger.error("API Exception: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .status(ex.getStatus().value())
                .message(ex.getMessage())
                .build();

        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message("Request failed: " + ex.getMessage())
                .error(errorResponse)
                .build();

        return new ResponseEntity<>(apiResponse, ex.getStatus());
    }

    /**
     * Handle validation errors for @Valid annotated request bodies
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            org.springframework.http.HttpStatusCode status,
            WebRequest request) {

        logger.error("Validation error: {}", ex.getMessage());

        // Collect all validation errors
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        // Format error message
        String errorMessage = errors.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .details(errorMessage)
                .build();

        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message("Validation failed")
                .error(errorResponse)
                .data(errors)
                .build();

        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle constraint violations
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
            ConstraintViolationException ex) {

        logger.error("Constraint violation: {}", ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("CONSTRAINT_VIOLATION")
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .details(ex.getMessage())
                .build();

        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message("Validation failed")
                .error(errorResponse)
                .build();

        return new ResponseEntity<>(apiResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle all other unexpected exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An unexpected error occurred")
                .build();

        ApiResponse<Object> apiResponse = ApiResponse.builder()
                .success(false)
                .message("Server error: " + ex.getMessage())
                .error(errorResponse)
                .build();

        return new ResponseEntity<>(apiResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handle file upload exceptions
     *
     * @param ex The exception
     * @return API response with error details
     */
    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileUploadException(FileUploadException ex) {
        logger.error("File upload exception: {}", ex.getMessage(), ex);

        ErrorResponse errorResponse = ErrorResponse.of(
                "FILE_UPLOAD_ERROR",
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errorResponse, "File upload failed"));
    }
}