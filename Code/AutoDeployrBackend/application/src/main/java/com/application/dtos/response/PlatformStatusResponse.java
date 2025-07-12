package com.application.dtos.response;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for platform status information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformStatusResponse {
    /**
     * List of active function names
     */
    private List<String> activeFunctions;

    /**
     * Path to the build directory
     */
    private String buildDirectory;

    /**
     * Overall system status: "healthy", "degraded", "error"
     */
    private String systemStatus;

    /**
     * Validate the response
     *
     * @throws ValidationException If validation fails
     */
    public void validate() {
        if (activeFunctions == null) {
            activeFunctions = new ArrayList<>();
        }

        if (buildDirectory == null || buildDirectory.trim().isEmpty()) {
            throw new ValidationException("buildDirectory", "Build directory cannot be empty");
        }

        if (systemStatus == null || systemStatus.trim().isEmpty()) {
            throw new ValidationException("systemStatus", "System status cannot be empty");
        }

        String normalizedStatus = systemStatus.toLowerCase();
        if (!normalizedStatus.equals("healthy") &&
                !normalizedStatus.equals("degraded") &&
                !normalizedStatus.equals("error")) {
            throw new ValidationException("systemStatus",
                    "System status must be one of: healthy, degraded, error");
        }
    }

    /**
     * Check if the system is in error state
     *
     * @return true if error, false otherwise
     */
    public boolean isError() {
        return "error".equalsIgnoreCase(systemStatus);
    }

}