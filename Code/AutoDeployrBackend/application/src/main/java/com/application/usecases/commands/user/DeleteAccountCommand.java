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
public class DeleteAccountCommand {
    private String userId;

    /**
     * Validate the command
     *
     * @throws ValidationException If the command is invalid
     */
    public void validate() {
        if (userId == null || userId.trim().isEmpty()) {
            throw new ValidationException("userId", "User ID cannot be empty");
        }
    }
}