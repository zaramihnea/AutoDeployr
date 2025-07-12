package com.application.usecases.commands.user;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordCommand {
    private String userId;
    private String currentPassword;
    private String newPassword;
    private String confirmPassword;

    /**
     * Validate the command
     *
     * @throws ValidationException If the command is invalid
     */
    public void validate() {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("userId", "User ID cannot be empty");
        }

        if (currentPassword == null || currentPassword.trim().isEmpty()) {
            throw new ValidationException("currentPassword", "Current password cannot be empty");
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new ValidationException("newPassword", "New password cannot be empty");
        }

        if (newPassword.length() < 6 || newPassword.length() > 100) {
            throw new ValidationException("newPassword", "New password must be between 6 and 100 characters");
        }

        if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
            throw new ValidationException("confirmPassword", "Passwords do not match");
        }
    }
}