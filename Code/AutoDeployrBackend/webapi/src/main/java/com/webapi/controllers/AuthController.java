package com.webapi.controllers;

import com.application.dtos.request.auth.LoginRequest;
import com.application.dtos.request.auth.SignupRequest;
import com.application.dtos.response.ApiResponse;
import com.application.dtos.response.auth.TokenResponse;
import com.application.usecases.commands.auth.LoginCommand;
import com.application.usecases.commands.auth.SignupCommand;
import com.application.usecases.commandhandlers.auth.LoginCommandHandler;
import com.application.usecases.commandhandlers.auth.SignupCommandHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "API for user authentication operations")
@RequiredArgsConstructor
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final LoginCommandHandler loginCommandHandler;
    private final SignupCommandHandler signupCommandHandler;

    /**
     * Login user
     *
     * @param request Login request with username and password
     * @return JWT token response
     */
    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticate user and return JWT token")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login request for user: {}", request.getUsername());

        LoginCommand command = LoginCommand.builder()
                .username(request.getUsername())
                .password(request.getPassword())
                .build();

        TokenResponse tokenResponse = loginCommandHandler.handle(command);

        return ResponseEntity.ok(
                ApiResponse.<TokenResponse>builder()
                        .success(true)
                        .message("Login successful")
                        .data(tokenResponse)
                        .build()
        );
    }

    /**
     * Signup new user
     *
     * @param request Signup request with user details
     * @return Success response with user ID
     */
    @PostMapping("/signup")
    @Operation(summary = "Register user", description = "Create a new user account")
    public ResponseEntity<ApiResponse<Map<String, String>>> signup(@Valid @RequestBody SignupRequest request) {
        logger.info("Signup request for user: {}", request.getUsername());

        SignupCommand command = SignupCommand.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(request.getPassword())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        String userId = signupCommandHandler.handle(command);

        Map<String, String> responseData = new HashMap<>();
        responseData.put("userId", userId);
        responseData.put("username", request.getUsername());

        return ResponseEntity.ok(
                ApiResponse.<Map<String, String>>builder()
                        .success(true)
                        .message("User registered successfully")
                        .data(responseData)
                        .build()
        );
    }

    /**
     * Logout user
     *
     * @return Success response
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Invalidate user session (client-side token deletion)")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("Logout successful")
                        .build()
        );
    }
}