package com.webapi.controllers;

import com.application.dtos.request.user.ResetPasswordRequest;
import com.application.dtos.response.ApiResponse;
import com.application.dtos.response.user.UserResponse;
import com.application.usecases.commands.user.DeleteAccountCommand;
import com.application.usecases.commands.user.ResetPasswordCommand;
import com.application.usecases.commandhandlers.user.DeleteAccountCommandHandler;
import com.application.usecases.commandhandlers.user.ResetPasswordCommandHandler;
import com.application.usecases.queries.user.GetUserDataQuery;
import com.application.usecases.queryhandlers.user.GetUserDataQueryHandler;
import com.infrastructure.security.UserPrincipal;
import com.webapi.utils.ResponseBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * Controller for user-related operations
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "API for user operations")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final GetUserDataQueryHandler getUserDataQueryHandler;
    private final ResetPasswordCommandHandler resetPasswordCommandHandler;
    private final DeleteAccountCommandHandler deleteAccountCommandHandler;

    /**
     * Get current user data
     *
     * @param currentUser Currently authenticated user
     * @return User data
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user data", description = "Retrieve details of the currently authenticated user")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        logger.info("Getting data for current user: {}", currentUser.getUsername());

        GetUserDataQuery query = GetUserDataQuery.builder()
                .userId(currentUser.getId())
                .build();

        UserResponse userResponse = getUserDataQueryHandler.handle(query);

        return ResponseBuilder.success("User data retrieved successfully", userResponse);
    }

    /**
     * Reset user password
     *
     * @param currentUser Currently authenticated user
     * @param request Reset password request
     * @return Success response
     */
    @PutMapping("/password")
    @Operation(summary = "Reset password", description = "Change the current user's password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ResetPasswordRequest request) {
        logger.info("Reset password request for user: {}", currentUser.getUsername());

        ResetPasswordCommand command = ResetPasswordCommand.builder()
                .userId(currentUser.getId())
                .currentPassword(request.getCurrentPassword())
                .newPassword(request.getNewPassword())
                .confirmPassword(request.getConfirmPassword())
                .build();

        resetPasswordCommandHandler.handle(command);

        return ResponseBuilder.success("Password reset successfully");
    }

    /**
     * Delete user
     *
     * @param currentUser Currently authenticated user
     * @return Success response
     */
    @DeleteMapping("/me")
    @Operation(summary = "Delete account", description = "Delete the current user's account")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@AuthenticationPrincipal UserPrincipal currentUser) {
        logger.info("Delete account request for user: {}", currentUser.getUsername());

        DeleteAccountCommand command = DeleteAccountCommand.builder()
                .userId(currentUser.getId())
                .build();

        deleteAccountCommandHandler.handle(command);

        return ResponseBuilder.success("Account deleted successfully");
    }
}