package com.domain.entities;

import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result of analyzing an application
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationAnalysisResult {
    private String language;  // e.g., "python", "java"
    private String framework; // e.g., "flask", "spring"

    @Builder.Default
    private String appName = "app";

    @Builder.Default
    private List<Route> routes = new ArrayList<>();

    @Builder.Default
    private Map<String, Map<String, String>> functions = new HashMap<>();  // name -> {source, file}

    @Builder.Default
    private Map<String, Map<String, String>> classes = new HashMap<>();    // name -> {source, file}

    @Builder.Default
    private Map<String, List<Function.ImportDefinition>> imports = new HashMap<>();  // file -> imports

    @Builder.Default
    private Map<String, Set<String>> functionCalls = new HashMap<>();  // function -> called functions

    @Builder.Default
    private Map<String, Map<String, String>> globalVars = new HashMap<>();  // name -> {source, file}

    @Builder.Default
    private List<Function.ImportDefinition> dbImports = new ArrayList<>();

    @Builder.Default
    private Map<String, Map<String, String>> dbCode = new HashMap<>();  // name -> {source, file}

    @Builder.Default
    private boolean dbDetected = false;

    @Builder.Default
    private Map<String, Map<String, String>> configCode = new HashMap<>();  // name -> {source, file}

    @Builder.Default
    private Set<String> envVars = new HashSet<>();

    /**
     * Validate the analysis result
     *
     * @throws ValidationException If the analysis result is invalid
     */
    public void validate() {
        if (language == null || language.trim().isEmpty()) {
            throw new ValidationException("language", "Programming language cannot be empty");
        }

        if (framework == null || framework.trim().isEmpty()) {
            throw new ValidationException("framework", "Framework cannot be empty");
        }

        if (appName == null || appName.trim().isEmpty()) {
            throw new ValidationException("appName", "Application name cannot be empty");
        }

        // Validate all routes
        if (routes != null) {
            for (Route route : routes) {
                route.validate();
            }
        }

        if (dbImports != null) {
            for (Function.ImportDefinition importDef : dbImports) {
                importDef.validate();
            }
        }
        
        if (imports != null) {
            for (Map.Entry<String, List<Function.ImportDefinition>> entry : imports.entrySet()) {
                if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                    throw new ValidationException("imports", "File path cannot be empty");
                }

                if (entry.getValue() != null) {
                    for (Function.ImportDefinition importDef : entry.getValue()) {
                        importDef.validate();
                    }
                }
            }
        }
    }

}