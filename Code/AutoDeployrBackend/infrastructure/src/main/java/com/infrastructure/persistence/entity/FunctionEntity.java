package com.infrastructure.persistence.entity;

import com.infrastructure.exceptions.PersistenceException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JPA entity for storing function data
 */
@Entity
@Table(
    name = "functions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_function_name_user", columnNames = {"name", "user_id"})
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class FunctionEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "path", nullable = false)
    private String path;

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "method")
    private List<String> methods = new ArrayList<>();

    @Lob
    @Column(name = "source", columnDefinition = "TEXT")
    private String source;

    @Column(name = "project_id")
    private String projectId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "language")
    private String language;

    @Column(name = "framework")
    private String framework;

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "dependency")
    private Set<String> dependencies = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "dependency_source", columnDefinition = "TEXT")
    @MapKeyColumn(name = "dependency_name")
    private Map<String, String> dependencySources = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "global_var")
    @MapKeyColumn(name = "var_name")
    private Map<String, String> globalVars = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "db_code")
    @MapKeyColumn(name = "code_name")
    private Map<String, String> dbCode = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "config_code")
    @MapKeyColumn(name = "config_name")
    private Map<String, String> configCode = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "env_var")
    private Set<String> envVars = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Column(name = "class_code")
    @MapKeyColumn(name = "class_name")
    private Map<String, String> classes = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Lob
    @Column(name = "imports_json", columnDefinition = "TEXT")
    private String importsJson;

    @Lob
    @Column(name = "db_imports_json", columnDefinition = "TEXT")
    private String dbImportsJson;

    @Column(name = "is_private", columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    @Builder.Default
    private boolean isPrivate = false;

    @Column(name = "api_key", length = 255)
    private String apiKey;

    @Column(name = "api_key_generated_at")
    private LocalDateTime apiKeyGeneratedAt;

    /**
     * Validate entity before persistence
     */
    @PrePersist
    @PreUpdate
    private void validate() {
        if (id == null || id.trim().isEmpty()) {
            throw new PersistenceException("Entity validation", "Function ID cannot be null or empty");
        }

        if (name == null || name.trim().isEmpty()) {
            throw new PersistenceException("Entity validation", "Function name cannot be null or empty");
        }

        if (path == null || path.trim().isEmpty()) {
            throw new PersistenceException("Entity validation", "Function path cannot be null or empty");
        }

        if (methods == null) {
            methods = new ArrayList<>();
        }

        if (methods.isEmpty()) {
            methods.add("GET");
        }

        if (source == null) {
            throw new PersistenceException("Entity validation", "Function source cannot be null");
        }

        if (dependencies == null) {
            dependencies = new HashSet<>();
        }

        if (dependencySources == null) {
            dependencySources = new HashMap<>();
        }

        if (globalVars == null) {
            globalVars = new HashMap<>();
        }

        if (dbCode == null) {
            dbCode = new HashMap<>();
        }

        if (configCode == null) {
            configCode = new HashMap<>();
        }

        if (envVars == null) {
            envVars = new HashSet<>();
        }

        if (classes == null) {
            classes = new HashMap<>();
        }

        if (isPrivate && (apiKey == null || apiKey.trim().isEmpty())) {
            throw new PersistenceException("Entity validation", "Private functions must have an API key");
        }
        if (isPrivate && apiKeyGeneratedAt == null) {
            throw new PersistenceException("Entity validation", "Private functions must have an API key generation timestamp");
        }
    }
}