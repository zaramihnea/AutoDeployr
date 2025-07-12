package com.application.dtos.request.auth;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Username cannot be empty")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    private String password;

    /**
     * Validate the request
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("username", "Username cannot be empty");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new ValidationException("password", "Password cannot be empty");
        }
    }
}