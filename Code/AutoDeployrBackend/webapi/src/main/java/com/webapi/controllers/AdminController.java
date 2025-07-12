package com.webapi.controllers;

import com.application.dtos.request.user.ChangeUserRoleRequest;
import com.application.dtos.response.ApiResponse;
import com.application.dtos.response.PlatformStatusResponse;
import com.application.dtos.response.user.UserResponse;
import com.application.usecases.commands.user.ChangeUserRoleCommand;
import com.application.usecases.commandhandlers.user.ChangeUserRoleCommandHandler;
import com.application.usecases.queries.function.GetDeployedFunctionsQuery;
import com.application.usecases.queries.function.GetPlatformStatusQuery;
import com.application.usecases.queries.user.GetUserDataQuery;
import com.application.usecases.queryhandlers.function.GetDeployedFunctionsQueryHandler;
import com.application.usecases.queryhandlers.function.GetPlatformStatusQueryHandler;
import com.application.usecases.queryhandlers.user.GetUserDataQueryHandler;
import com.domain.entities.Function;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Administration", description = "API for admin-only operations")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    //NOT USED YET

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final ChangeUserRoleCommandHandler changeUserRoleCommandHandler;
    private final GetUserDataQueryHandler getUserDataQueryHandler;
    private final GetPlatformStatusQueryHandler getPlatformStatusQueryHandler;
    private final GetDeployedFunctionsQueryHandler getDeployedFunctionsQueryHandler;

    /**
     * Change user roles
     *
     * @param request Change role request
     * @return Updated user
     */
    @PutMapping("/users/{userId}/roles")
    @Operation(summary = "Change user roles", description = "Update a user's roles")
    public ResponseEntity<ApiResponse<UserResponse>> changeUserRoles(
            @PathVariable String userId,
            @Valid @RequestBody ChangeUserRoleRequest request) {
        request.setUserId(userId);

        logger.info("Admin changing roles for user ID: {} to roles: {}",
                userId, request.getRoles());

        ChangeUserRoleCommand command = ChangeUserRoleCommand.builder()
                .userId(userId)
                .roles(request.getRoles())
                .build();

        UserResponse userResponse = changeUserRoleCommandHandler.handle(command);

        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .success(true)
                        .message("User roles updated successfully")
                        .data(userResponse)
                        .build()
        );
    }

    /**
     * Get user by ID (admin access)
     *
     * @param userId ID of the user to fetch
     * @return User data
     */
    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by ID", description = "Retrieve details of any user")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable String userId) {
        logger.info("Admin requesting data for user ID: {}", userId);

        GetUserDataQuery query = GetUserDataQuery.builder()
                .userId(userId)
                .build();

        UserResponse userResponse = getUserDataQueryHandler.handle(query);

        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .success(true)
                        .message("User data retrieved successfully")
                        .data(userResponse)
                        .build()
        );
    }

    /**
     * Get platform status
     *
     * @return Platform status information
     */
    @GetMapping("/status")
    @Operation(summary = "Get platform status",
            description = "Retrieve status information about the serverless platform")
    public ResponseEntity<ApiResponse<PlatformStatusResponse>> getPlatformStatus() {
        logger.info("Received request for platform status");

        GetPlatformStatusQuery query = new GetPlatformStatusQuery();
        PlatformStatusResponse status = getPlatformStatusQueryHandler.handle(query);

        logger.info("Platform status: {} active functions", status.getActiveFunctions().size());

        return ResponseEntity.ok(
                ApiResponse.<PlatformStatusResponse>builder()
                        .success(true)
                        .message("Platform status retrieved successfully")
                        .data(status)
                        .build()
        );
    }

    /**
     * Get all deployed functions
     *
     * @return List of deployed functions
     */
    @GetMapping("/functions")
    @Operation(summary = "Get all deployed functions",
            description = "Retrieve all functions currently deployed on the serverless platform")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllFunctions() {
        logger.info("Received request to get all deployed functions");

        GetDeployedFunctionsQuery query = new GetDeployedFunctionsQuery();
        List<Function> functions = getDeployedFunctionsQueryHandler.handle(query);
        List<Map<String, Object>> functionData = functions.stream()
                .map(function -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", function.getName());
                    data.put("path", function.getPath());
                    data.put("methods", function.getMethods());
                    data.put("appName", function.getAppName());
                    return data;
                })
                .collect(Collectors.toList());

        logger.info("Returning {} deployed functions", functionData.size());

        return ResponseEntity.ok(
                ApiResponse.<List<Map<String, Object>>>builder()
                        .success(true)
                        .message("Functions retrieved successfully")
                        .data(functionData)
                        .build()
        );
    }
}