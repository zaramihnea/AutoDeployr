package com.domain.entities;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a container for a function
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Container {
    private String id;
    private String functionName;

    /**
     * Validate the container
     *
     * @throws ValidationException If the container is invalid
     */
    public void validate() {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("id", "Container ID cannot be empty");
        }

        if (functionName == null || functionName.trim().isEmpty()) {
            throw new ValidationException("functionName", "Function name cannot be empty");
        }
    }

}