package com.domain.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.domain.exceptions.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Domain entity representing a function analyzed from source code.
 * Includes annotations for mapping from JSON (snake_case) generated by the Python analyzer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Function {
    private String id;
    private String name;
    private String path;
    private List<String> methods;
    private String source;
    private String route;
    private List<String> httpMethods;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("app_name")
    @Builder.Default
    private String appName = "app";

    @JsonProperty("dependencies")
    @Builder.Default
    private Set<String> dependencies = new HashSet<>();

    @JsonProperty("dependency_sources")
    @Builder.Default
    private Map<String, String> dependencySources = new HashMap<>();

    @JsonProperty("imports")
    @Builder.Default
    private List<ImportDefinition> imports = new ArrayList<>();

    @Builder.Default
    private Set<String> copiedModules = new HashSet<>();

    @JsonProperty("classes")
    @Builder.Default
    private Map<String, String> classes = new HashMap<>();

    @JsonProperty("global_vars")
    @Builder.Default
    private Map<String, String> globalVars = new HashMap<>();

    @JsonProperty("db_code")
    @Builder.Default
    private Map<String, String> dbCode = new HashMap<>();

    @JsonProperty("db_imports")
    @Builder.Default
    private List<ImportDefinition> dbImports = new ArrayList<>();

    @JsonProperty("config_code")
    @Builder.Default
    private Map<String, String> configCode = new HashMap<>();

    @JsonProperty("env_vars")
    @Builder.Default
    private Set<String> envVars = new HashSet<>();

    @JsonProperty("language")
    @Builder.Default
    private String language = "python";

    @JsonProperty("framework")
    @Builder.Default
    private String framework = "flask";

    @JsonProperty("file_path")
    private String filePath;

    @JsonProperty("line_number")
    private int lineNumber;

    @JsonProperty("requires_db")
    private boolean requiresDb;

    @Builder.Default
    private long invocationCount = 0;

    @Builder.Default
    private long totalExecutionTimeMs = 0;

    @Builder.Default
    private long lastExecutionTimeMs = 0;

    @Builder.Default
    private LocalDateTime lastInvoked = null;

    /**
     * Whether this function requires authentication (private) or not (public)
     */
    @Builder.Default
    private boolean isPrivate = false;

    /**
     * API key for accessing private functions
     */
    private String apiKey;

    /**
     * When the API key was generated
     */
    private LocalDateTime apiKeyGeneratedAt;

    /**
     * Validate the function entity state after creation or modification.
     * Ensures essential fields are present and valid.
     *
     * @throws ValidationException If the function state is invalid.
     */
    public void validate() {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("name", "Function name cannot be empty");
        }
        if (path == null || path.trim().isEmpty()) {
            throw new ValidationException("path", "Function path cannot be empty");
        }
        if (source == null || source.trim().isEmpty()) {
            throw new ValidationException("source", "Function source code cannot be empty");
        }

        if (methods == null) {
            methods = new ArrayList<>();
        }
        if (methods.isEmpty()) {
            methods.add("GET"); // Default to GET method
        }

        for (String method : methods) {
            if (!isValidHttpMethod(method)) {
                throw new ValidationException("methods", "Invalid HTTP method specified: '" + method + "' for function '" + name + "'");
            }
        }

        if (dependencies == null) dependencies = new HashSet<>();
        if (dependencySources == null) dependencySources = new HashMap<>();
        if (imports == null) imports = new ArrayList<>();
        if (copiedModules == null) copiedModules = new HashSet<>();
        if (classes == null) classes = new HashMap<>();
        if (globalVars == null) globalVars = new HashMap<>();
        if (dbCode == null) dbCode = new HashMap<>();
        if (dbImports == null) dbImports = new ArrayList<>();
        if (configCode == null) configCode = new HashMap<>();
        if (envVars == null) envVars = new HashSet<>();
        if (httpMethods == null) httpMethods = new ArrayList<>();

        for (ImportDefinition imp : imports) {
            if (imp != null) imp.validate(); else throw new ValidationException("imports", "Null ImportDefinition found in imports list.");
        }
        for (ImportDefinition imp : dbImports) {
            if (imp != null) imp.validate(); else throw new ValidationException("dbImports", "Null ImportDefinition found in dbImports list.");
        }

        if (language == null || language.trim().isEmpty()) {
            throw new ValidationException("language", "Function language cannot be empty");
        }
        if (framework == null || framework.trim().isEmpty()) {
            throw new ValidationException("framework", "Function framework cannot be empty");
        }
        if (invocationCount < 0) {
            throw new ValidationException("invocationCount", "Invocation count cannot be negative");
        }
        if (totalExecutionTimeMs < 0) {
            throw new ValidationException("totalExecutionTimeMs", "Total execution time cannot be negative");
        }
        if (lastExecutionTimeMs < 0) {
            throw new ValidationException("lastExecutionTimeMs", "Last execution time cannot be negative");
        }
        if (lastInvoked!= null && lastInvoked.isAfter(LocalDateTime.now())) {
            throw new ValidationException("lastInvoked", "Last invoked time cannot be in the future");
        }
        if (isPrivate && (apiKey == null || apiKey.trim().isEmpty())) {
            throw new ValidationException("apiKey", "Private functions must have an API key");
        }
        if (isPrivate && apiKeyGeneratedAt == null) {
            throw new ValidationException("apiKeyGeneratedAt", "Private functions must have an API key generation timestamp");
        }
    }

    /**
     * Checks if a given string represents a standard HTTP method.
     * Case-insensitive comparison.
     *
     * @param method The HTTP method string to check.
     * @return {@code true} if the method is valid, {@code false} otherwise.
     */
    private boolean isValidHttpMethod(String method) {
        if (method == null || method.isBlank()) return false;
        String normalizedMethod = method.trim().toUpperCase();
        return normalizedMethod.equals("GET") ||
                normalizedMethod.equals("POST") ||
                normalizedMethod.equals("PUT") ||
                normalizedMethod.equals("DELETE") ||
                normalizedMethod.equals("PATCH") ||
                normalizedMethod.equals("OPTIONS") ||
                normalizedMethod.equals("HEAD");
    }

    /**
     * Inner class representing a single import statement (e.g., 'import module as alias').
     * Used for lists like 'imports' and 'dbImports'.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ImportDefinition {
        private String module;
        private String alias;

        /**
         * Validates the import definition.
         * @throws ValidationException If module or alias is empty.
         */
        public void validate() {
            if (module == null || module.trim().isEmpty()) {
                throw new ValidationException("module", "Import module name cannot be empty.");
            }
            if (alias == null || alias.trim().isEmpty()) {
                throw new ValidationException("alias", "Import alias cannot be empty (can be same as module name).");
            }
        }
    }

    /**
     * Record a function execution
     *
     * @param executionTimeMs Execution time in milliseconds
     * @param successful Whether the execution was successful
     */
    public void recordExecution(long executionTimeMs, boolean successful) {
        this.invocationCount++;
        this.totalExecutionTimeMs += executionTimeMs;
        this.lastExecutionTimeMs = executionTimeMs;
        this.lastInvoked = LocalDateTime.now();
    }

    /**
     * Get average execution time in milliseconds
     *
     * @return Average execution time or 0 if no executions
     */
    public long getAverageExecutionTimeMs() {
        return invocationCount > 0 ? totalExecutionTimeMs / invocationCount : 0;
    }
}