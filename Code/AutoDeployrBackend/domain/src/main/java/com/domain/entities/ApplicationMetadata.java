package com.domain.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents application-level metadata for a deployed application
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationMetadata {
    /**
     * Application name
     */
    private String appName;

    /**
     * Original application path
     */
    private String appPath;

    /**
     * Application deployment timestamp
     */
    @Builder.Default
    private LocalDateTime deployedAt = LocalDateTime.now();

    /**
     * Last updated timestamp
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * List of deployed functions within this application
     */
    @Builder.Default
    private List<String> deployedFunctions = new ArrayList<>();

    /**
     * User ID of the owner
     */
    private String ownerId;

    /**
     * Additional application properties
     */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();
}