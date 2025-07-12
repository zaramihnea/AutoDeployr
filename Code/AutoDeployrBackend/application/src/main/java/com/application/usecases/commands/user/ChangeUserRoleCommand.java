package com.application.usecases.commands.user;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeUserRoleCommand {
    private String userId;
    private Set<String> roles;

    /**
     * Validate the command
     *
     * @throws ValidationException If the command is invalid
     */
    public void validate() {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("userId", "User ID cannot be empty");
        }

        if (roles == null || roles.isEmpty()) {
            throw new ValidationException("roles", "Roles cannot be empty");
        }

        for (String role : roles) {
            if (!role.startsWith("ROLE_")) {
                throw new ValidationException("roles", "Role '" + role + "' must start with 'ROLE_'");
            }
        }
    }
}