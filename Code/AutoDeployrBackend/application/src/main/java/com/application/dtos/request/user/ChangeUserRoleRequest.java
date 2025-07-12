package com.application.dtos.request.user;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeUserRoleRequest {
    private String userId;

    @NotEmpty(message = "Roles cannot be empty")
    private Set<String> roles;

    /**
     * Validate the request
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
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