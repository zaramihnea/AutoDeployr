package com.domain.entities;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    private String id;
    private String username;
    private String email;
    private String password;
    private String firstName;
    private String lastName;

    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Builder.Default
    private boolean active = true;

    /**
     * Validate the user entity
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
        if (email == null || email.trim().isEmpty()) {
            throw new ValidationException("email", "Email cannot be empty");
        }

        if (username == null || username.trim().isEmpty()) {
            throw new ValidationException("username", "Username cannot be empty");
        }

        if (password == null || password.trim().isEmpty()) {
            throw new ValidationException("password", "Password cannot be empty");
        }
    }

    /**
     * Add a role to the user
     *
     * @param role Role to add
     */
    public void addRole(String role) {
        if (roles == null) {
            roles = new HashSet<>();
        }
        roles.add(role);
    }

}