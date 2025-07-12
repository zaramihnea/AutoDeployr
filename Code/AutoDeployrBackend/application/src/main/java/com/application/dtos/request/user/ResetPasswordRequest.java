package com.application.dtos.request.user;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {
    @NotBlank(message = "Current password cannot be empty")
    private String currentPassword;

    @NotBlank(message = "New password cannot be empty")
    @Size(min = 6, max = 100, message = "New password must be between 6 and 100 characters")
    private String newPassword;

    @NotBlank(message = "Confirm password cannot be empty")
    private String confirmPassword;

    /**
     * Validate the request
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
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