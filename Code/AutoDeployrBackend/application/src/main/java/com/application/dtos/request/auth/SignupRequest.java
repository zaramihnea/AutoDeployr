package com.application.dtos.request.auth;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {
    @NotBlank(message = "Username cannot be empty")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;

    private String firstName;
    private String lastName;

    /**
     * Validate the request
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("username", "Username cannot be empty");
        }

        if (username.length() < 3 || username.length() > 50) {
            throw new ValidationException("username", "Username must be between 3 and 50 characters");
        }

        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("email", "Email cannot be empty");
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ValidationException("email", "Email should be valid");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new ValidationException("password", "Password cannot be empty");
        }

        if (password.length() < 6 || password.length() > 100) {
            throw new ValidationException("password", "Password must be between 6 and 100 characters");
        }
    }
}