package com.application.usecases.commands.auth;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupCommand {
    private String username;
    private String email;
    private String password;
    private String firstName;
    private String lastName;

    /**
     * Validate the command
     *
     * @throws ValidationException If the command is invalid
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

        // Simple email validation
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