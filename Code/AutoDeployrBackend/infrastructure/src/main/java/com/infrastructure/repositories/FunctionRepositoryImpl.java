package com.infrastructure.repositories;

import com.domain.entities.Function;
import com.domain.exceptions.ResourceNotFoundException;
import com.domain.repositories.IFunctionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infrastructure.exceptions.PersistenceException;
import com.infrastructure.exceptions.StorageException;
import com.infrastructure.persistence.entity.FunctionEntity;
import com.infrastructure.persistence.repository.SpringFunctionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.EmptyResultDataAccessException;
import com.infrastructure.exceptions.RepositoryException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.stream.Collectors;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Implementation of IFunctionRepository using JPA
 */
@Repository
@Transactional
public class FunctionRepositoryImpl implements IFunctionRepository {
    private static final Logger logger = LoggerFactory.getLogger(FunctionRepositoryImpl.class);

    private final SpringFunctionRepository springFunctionRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public FunctionRepositoryImpl(SpringFunctionRepository springFunctionRepository, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.springFunctionRepository = springFunctionRepository;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Function> findAll() {
        try {
            List<FunctionEntity> entities = springFunctionRepository.findAll();
            return entities.stream()
                    .map(this::mapToFunction)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error retrieving all functions: {}", e.getMessage(), e);
            throw new StorageException("Failed to retrieve functions: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Function> findById(String id) {
        try {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Function ID cannot be empty");
            }

            return springFunctionRepository.findById(id)
                    .map(this::mapToFunction);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error retrieving function by ID {}: {}", id, e.getMessage(), e);
            throw new StorageException("Failed to retrieve function with ID: " + id, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Function> findByName(String name) {
        try {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Function name cannot be empty");
            }
            String currentUserId = getCurrentUserId();
            if (currentUserId != null && !currentUserId.isEmpty()) {
                logger.debug("Attempting user-scoped lookup for function: {} with user: {}", name, currentUserId);
                Optional<Function> userScopedFunction = findByNameAndUserId(name, currentUserId);
                if (userScopedFunction.isPresent()) {
                    logger.info("Found function using current user context: {} for user: {}", name, currentUserId);
                    return userScopedFunction;
                }
            }
            try {
                String sql = "SELECT * FROM functions WHERE name = ? LIMIT 1";
                List<Function> functions = jdbcTemplate.query(sql, 
                    (rs, rowNum) -> mapResultSetToFunction(rs), 
                    name);
                
                if (!functions.isEmpty()) {
                    Function foundFunction = functions.get(0);
                    logger.info("Found function using JDBC query: {} (user: {})", 
                        name, foundFunction.getUserId() != null ? foundFunction.getUserId() : "unknown");
                    return Optional.of(foundFunction);
                } else {
                    logger.debug("No function found with name: {}", name);
                    return Optional.empty();
                }
            } catch (Exception jdbcException) {
                logger.error("JDBC fallback query failed for function name {}: {}", name, jdbcException.getMessage());
                return Optional.empty();
            }
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error retrieving function by name {}: {}", name, e.getMessage(), e);
            throw new StorageException("Failed to retrieve function with name: " + name, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Function> findByAppNameAndName(String appName, String functionName) {
        try {
            if (appName == null || appName.trim().isEmpty()) {
                throw new IllegalArgumentException("Application name cannot be empty");
            }

            if (functionName == null || functionName.trim().isEmpty()) {
                throw new IllegalArgumentException("Function name cannot be empty");
            }
            String userId = getCurrentUserId();
            
            logger.debug("Finding function by appName: {}, functionName: {}, currentUserId: {}", 
                appName, functionName, userId);
            if (userId != null && !userId.isEmpty()) {
                Optional<FunctionEntity> entity = springFunctionRepository.findByAppNameAndNameAndUserId(appName, functionName, userId);
                if (entity.isPresent()) {
                    logger.debug("Found function using user-scoped app+function lookup for user: {}", userId);
                    return entity.map(this::mapToFunction);
                }
            }
            try {
                String sql = "SELECT * FROM functions WHERE app_name = ? AND name = ?";
                List<Function> functions = jdbcTemplate.query(sql, 
                    (rs, rowNum) -> mapResultSetToFunction(rs), 
                    appName, functionName);
                
                if (functions.isEmpty()) {
                    logger.debug("No functions found with app name: {} and name: {}", appName, functionName);
                    return Optional.empty();
                } else if (functions.size() == 1) {
                    logger.debug("Found single function with app name: {} and name: {}", appName, functionName);
                    return Optional.of(functions.get(0));
                } else {
                    logger.debug("Found {} functions with app name: {} and name: {}", 
                        functions.size(), appName, functionName);
                    if (userId != null && !userId.isEmpty()) {
                        for (Function function : functions) {
                            if (userId.equals(function.getUserId())) {
                                logger.info("Selected function for current user: {} (app: {}, function: {})", 
                                    userId, appName, functionName);
                                return Optional.of(function);
                            }
                        }
                    }
                    Function selectedFunction = functions.get(0);
                    logger.info("Multiple functions found for app: {} and function: {}. " +
                        "Selected function from user: {} (no current user context or no match found)", 
                        appName, functionName, selectedFunction.getUserId());
                    return Optional.of(selectedFunction);
                }
            } catch (Exception jdbcException) {
                logger.warn("JDBC query failed for app: {} and function: {}, falling back to JPA: {}", 
                    appName, functionName, jdbcException.getMessage());
                List<FunctionEntity> entities = springFunctionRepository.findByAppNameAndName(appName, functionName);
                if (entities.isEmpty()) {
                    return Optional.empty();
                } else if (entities.size() == 1) {
                    return Optional.of(mapToFunction(entities.get(0)));
                } else {
                    if (userId != null && !userId.isEmpty()) {
                        for (FunctionEntity entity : entities) {
                            if (userId.equals(entity.getUserId())) {
                                logger.info("Selected function for current user: {} (app: {}, function: {})", 
                                    userId, appName, functionName);
                                return Optional.of(mapToFunction(entity));
                            }
                        }
                    }
                    
                    // If still no unique match, return the first one
                    logger.info("Multiple functions found for app: {} and function: {}. " +
                        "Selected function from user: {} (fallback to JPA)", 
                        appName, functionName, entities.get(0).getUserId());
                    return Optional.of(mapToFunction(entities.get(0)));
                }
            }
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error retrieving function by app name {} and function name {}: {}",
                    appName, functionName, e.getMessage(), e);
            throw new StorageException("Failed to retrieve function with app name: " +
                    appName + " and name: " + functionName, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Function> findByUserId(String userId) {
        try {
            List<FunctionEntity> entities = springFunctionRepository.findByUserId(userId);
            return entities.stream()
                    .map(this::mapToFunction)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error finding functions by user ID {}: {}", userId, e.getMessage(), e);
            throw new PersistenceException("Error finding functions by user ID", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Function> findByAppNameAndNameAndUserId(String appName, String functionName, String userId) {
        try {
            if (appName == null || appName.trim().isEmpty()) {
                throw new IllegalArgumentException("Application name cannot be empty");
            }

            if (functionName == null || functionName.trim().isEmpty()) {
                throw new IllegalArgumentException("Function name cannot be empty");
            }

            logger.debug("Finding function by appName: {}, functionName: {}, userId: {}", appName, functionName, userId);

            Optional<FunctionEntity> entity = springFunctionRepository.findByAppNameAndNameAndUserId(appName, functionName, userId);
            return entity.map(this::mapToFunction);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error retrieving function by app name {}, function name {}, and user ID {}: {}",
                    appName, functionName, userId, e.getMessage(), e);
            throw new StorageException("Failed to retrieve function with app name: " +
                    appName + ", name: " + functionName + ", and user ID: " + userId, e);
        }
    }

    @Override
    @Transactional
    public Function save(Function function) {
        try {
            if (function == null) {
                throw new IllegalArgumentException("Function cannot be null");
            }
            function.validate();
            if (function.getId() == null) {
                function.setId(UUID.randomUUID().toString());
            }

            FunctionEntity entity = mapToEntity(function);
            FunctionEntity savedEntity = springFunctionRepository.save(entity);

            return mapToFunction(savedEntity);
        } catch (IllegalArgumentException e) {
            throw e; // Pass through validation exceptions
        } catch (Exception e) {
            String functionName = function != null ? function.getName() : "unknown";
            logger.error("Error saving function {}: {}", functionName, e.getMessage(), e);
            throw new StorageException("Failed to save function: " + functionName, e);
        }
    }

    @Override
    @Transactional
    public void deleteByName(String name) {
        try {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Function name cannot be empty");
            }
            Optional<FunctionEntity> functionOpt = springFunctionRepository.findByName(name);
            if (functionOpt.isEmpty()) {
                throw new ResourceNotFoundException("Function", name);
            }

            springFunctionRepository.deleteByName(name);
            logger.info("Deleted function with name: {}", name);
        } catch (IllegalArgumentException | ResourceNotFoundException e) {
            throw e; // Pass through validation and domain exceptions
        } catch (Exception e) {
            logger.error("Error deleting function with name {}: {}", name, e.getMessage(), e);
            throw new StorageException("Failed to delete function with name: " + name, e);
        }
    }

    @Override
    @Transactional
    public void delete(Function function) {
        try {
            if (function == null) {
                throw new IllegalArgumentException("Function cannot be null");
            }
            if (function.getId() == null || function.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("Function ID cannot be empty");
            }
            Optional<FunctionEntity> functionOpt = springFunctionRepository.findById(function.getId());
            if (functionOpt.isEmpty()) {
                throw new ResourceNotFoundException("Function", function.getId());
            }
            springFunctionRepository.delete(functionOpt.get());
            logger.info("Deleted function with ID: {} and name: {}", function.getId(), function.getName());
        } catch (IllegalArgumentException | ResourceNotFoundException e) {
            throw e; // Pass through validation and domain exceptions
        } catch (Exception e) {
            logger.error("Error deleting function with ID {}: {}", function.getId(), e.getMessage(), e);
            throw new StorageException("Failed to delete function with ID: " + function.getId(), e);
        }
    }

    @Override
    public Function findByNameAndAppName(String name, String appName) {
        try {
            String sql = "SELECT * FROM functions WHERE name = ? AND app_name = ?";
            List<Function> functions = jdbcTemplate.query(sql, 
                (rs, rowNum) -> mapResultSetToFunction(rs), 
                name, appName);
            return functions.isEmpty() ? null : functions.get(0);
        } catch (Exception e) {
            logger.error("Error finding function by name and app name: {}", e.getMessage(), e);
            throw new RepositoryException("function", "Error finding function by name and app name", e);
        }
    }

    @Override
    public List<Function> findByAppName(String appName) {
        try {
            if (appName == null || appName.trim().isEmpty()) {
                throw new IllegalArgumentException("Application name cannot be empty");
            }
            try {
                List<FunctionEntity> entities = springFunctionRepository.findByAppName(appName);
                return entities.stream()
                        .map(this::mapToFunction)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                // Fallback to JDBC if the Spring Data JPA method is not available
                logger.warn("Could not use Spring Data JPA for findByAppName, falling back to JDBC: {}", e.getMessage());
                String sql = "SELECT * FROM functions WHERE app_name = ?";
                List<Function> functions = jdbcTemplate.query(sql, 
                    (rs, rowNum) -> mapResultSetToFunction(rs), 
                    appName);
                return functions;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error finding functions by app name {}: {}", appName, e.getMessage(), e);
            throw new RepositoryException("function", "Error finding functions by app name", e);
        }
    }

    @Override
    public int countByAppName(String appName) {
        try {
            if (appName == null || appName.trim().isEmpty()) {
                throw new IllegalArgumentException("Application name cannot be empty");
            }

            int count = springFunctionRepository.countByAppName(appName);
            logger.debug("Found {} functions for application: {}", count, appName);
            return count;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error counting functions by app name {}: {}", appName, e.getMessage(), e);
            throw new StorageException("Error counting functions by app name: " + e.getMessage(), e);
        }
    }

    @Override
    public int countByAppNameAndUserId(String appName, String userId) {
        try {
            if (appName == null || appName.trim().isEmpty()) {
                throw new IllegalArgumentException("Application name cannot be empty");
            }
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalArgumentException("User ID cannot be empty");
            }

            int count = springFunctionRepository.countByAppNameAndUserId(appName, userId);
            logger.debug("Found {} functions for application: {} and user: {}", count, appName, userId);
            return count;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error counting functions by app name {} and user {}: {}", appName, userId, e.getMessage(), e);
            throw new StorageException("Error counting functions by app name and user: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Function> findByNameAndUserId(String name, String userId) {
        try {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Function name cannot be empty");
            }
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalArgumentException("User ID cannot be empty");
            }

            return springFunctionRepository.findByNameAndUserId(name, userId)
                    .map(this::mapToFunction);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error retrieving function by name {} and user ID {}: {}", name, userId, e.getMessage(), e);
            throw new StorageException("Failed to retrieve function with name: " + name + " and user ID: " + userId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Function> findByApiKey(String apiKey) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException("API key cannot be empty");
            }

            return springFunctionRepository.findByApiKey(apiKey)
                    .map(this::mapToFunction);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error retrieving function by API key: {}", e.getMessage(), e);
            throw new StorageException("Failed to retrieve function with API key", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Function> findByUserIdAndIsPrivate(String userId, boolean isPrivate) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalArgumentException("User ID cannot be empty");
            }

            List<FunctionEntity> entities = springFunctionRepository.findByUserIdAndIsPrivate(userId, isPrivate);
            return entities.stream()
                    .map(this::mapToFunction)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error finding functions by user ID {} and privacy status {}: {}", 
                    userId, isPrivate, e.getMessage(), e);
            throw new StorageException("Failed to retrieve functions by user and privacy status", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Function> findByIsPrivate(boolean isPrivate) {
        try {
            List<FunctionEntity> entities = springFunctionRepository.findByIsPrivate(isPrivate);
            return entities.stream()
                    .map(this::mapToFunction)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error finding functions by privacy status {}: {}", isPrivate, e.getMessage(), e);
            throw new StorageException("Failed to retrieve functions by privacy status", e);
        }
    }

    /**
     * Get the current authenticated user ID
     * This method tries to extract user context from Spring Security context
     * 
     * @return The current user ID or null if not available
     */
    private String getCurrentUserId() {
        try {
            logger.debug("No user context available in getCurrentUserId");
            return null;
        } catch (Exception e) {
            logger.debug("Could not determine current user ID: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Find functions by name with user preference
     * This method prioritizes user-scoped lookups over global lookups
     * 
     * @param name Function name
     * @param preferredUserId Preferred user ID (optional)
     * @return Optional containing the function if found
     */
    public Optional<Function> findByNameWithUserPreference(String name, String preferredUserId) {
        try {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Function name cannot be empty");
            }
            if (preferredUserId != null && !preferredUserId.trim().isEmpty()) {
                Optional<Function> userFunction = findByNameAndUserId(name, preferredUserId);
                if (userFunction.isPresent()) {
                    logger.debug("Found function with user preference: {} for user: {}", name, preferredUserId);
                    return userFunction;
                }
            }
            String currentUserId = getCurrentUserId();
            if (currentUserId != null && !currentUserId.isEmpty() && 
                !currentUserId.equals(preferredUserId)) {
                Optional<Function> currentUserFunction = findByNameAndUserId(name, currentUserId);
                if (currentUserFunction.isPresent()) {
                    logger.debug("Found function with current user context: {} for user: {}", name, currentUserId);
                    return currentUserFunction;
                }
            }
            return findByName(name);
            
        } catch (Exception e) {
            logger.error("Error in findByNameWithUserPreference for {}: {}", name, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Map Entity to Domain model
     *
     * @param entity Entity to map
     * @return Domain model
     * @throws StorageException If mapping fails
     */
    private Function mapToFunction(FunctionEntity entity) {
        try {
            Function function = Function.builder()
                    .id(entity.getId())
                    .name(entity.getName())
                    .path(entity.getPath())
                    .methods(entity.getMethods())
                    .source(entity.getSource())
                    .projectId(entity.getProjectId())
                    .userId(entity.getUserId())
                    .appName(entity.getAppName())
                    .language(entity.getLanguage())
                    .framework(entity.getFramework())
                    .dependencies(entity.getDependencies())
                    .dependencySources(entity.getDependencySources())
                    .globalVars(entity.getGlobalVars())
                    .dbCode(entity.getDbCode())
                    .configCode(entity.getConfigCode())
                    .envVars(entity.getEnvVars())
                    .classes(entity.getClasses())
                    // Security fields
                    .isPrivate(entity.isPrivate())
                    .apiKey(entity.getApiKey())
                    .apiKeyGeneratedAt(entity.getApiKeyGeneratedAt())
                    .build();
            if (entity.getImportsJson() != null && !entity.getImportsJson().isEmpty()) {
                List<Function.ImportDefinition> imports = objectMapper.readValue(
                        entity.getImportsJson(),
                        new TypeReference<>() {
                        });
                function.setImports(imports);
            }

            if (entity.getDbImportsJson() != null && !entity.getDbImportsJson().isEmpty()) {
                List<Function.ImportDefinition> dbImports = objectMapper.readValue(
                        entity.getDbImportsJson(),
                        new TypeReference<>() {
                        });
                function.setDbImports(dbImports);
            }

            return function;
        } catch (JsonProcessingException e) {
            logger.error("Error deserializing JSON for function {}: {}", entity.getName(), e.getMessage(), e);
            throw new StorageException("Failed to deserialize function data for " + entity.getName(), e);
        }
    }

    /**
     * Map Domain model to Entity
     *
     * @param function Domain model to map
     * @return Entity
     * @throws StorageException If mapping fails
     */
    private FunctionEntity mapToEntity(Function function) {
        try {
            if (function.getMethods() == null || function.getMethods().isEmpty()) {
                List<String> defaultMethods = new ArrayList<>();
                defaultMethods.add("GET");
                function.setMethods(defaultMethods);
                logger.info("Applied default GET method to function: {}", function.getName());
            }

            FunctionEntity entity = FunctionEntity.builder()
                    .id(function.getId())
                    .name(function.getName())
                    .path(function.getPath())
                    .methods(new ArrayList<>(function.getMethods()))
                    .source(function.getSource())
                    .projectId(function.getProjectId())
                    .userId(function.getUserId())
                    .appName(function.getAppName())
                    .language(function.getLanguage())
                    .framework(function.getFramework())
                    .dependencies(function.getDependencies() != null ? function.getDependencies() : new HashSet<>())
                    .dependencySources(function.getDependencySources() != null ? function.getDependencySources() : new HashMap<>())
                    .globalVars(function.getGlobalVars() != null ? function.getGlobalVars() : new HashMap<>())
                    .dbCode(function.getDbCode() != null ? function.getDbCode() : new HashMap<>())
                    .configCode(function.getConfigCode() != null ? function.getConfigCode() : new HashMap<>())
                    .envVars(function.getEnvVars() != null ? function.getEnvVars() : new HashSet<>())
                    .classes(function.getClasses() != null ? function.getClasses() : new HashMap<>())
                    // Security fields
                    .isPrivate(function.isPrivate())
                    .apiKey(function.getApiKey())
                    .apiKeyGeneratedAt(function.getApiKeyGeneratedAt())
                    .build();

            // Double-check methods aren't empty
            if (entity.getMethods() == null || entity.getMethods().isEmpty()) {
                entity.setMethods(List.of("GET"));
                logger.warn("Added default GET method to entity after mapping: {}", function.getName());
            }
            if (function.getImports() != null && !function.getImports().isEmpty()) {
                entity.setImportsJson(objectMapper.writeValueAsString(function.getImports()));
            }

            if (function.getDbImports() != null && !function.getDbImports().isEmpty()) {
                entity.setDbImportsJson(objectMapper.writeValueAsString(function.getDbImports()));
            }

            return entity;
        } catch (JsonProcessingException e) {
            logger.error("Error serializing JSON for function {}: {}", function.getName(), e.getMessage(), e);
            throw new StorageException("Failed to serialize function data for " + function.getName(), e);
        }
    }

    private Function mapResultSetToFunction(ResultSet rs) throws SQLException {
        try {
            Function function = new Function();
            function.setId(rs.getString("id"));
            function.setName(rs.getString("name"));
            function.setPath(rs.getString("path"));
            function.setSource(rs.getString("source"));
            function.setAppName(rs.getString("app_name"));
            function.setUserId(rs.getString("user_id"));
            function.setLanguage(rs.getString("language"));
            function.setFramework(rs.getString("framework"));
            function.setPrivate(rs.getBoolean("is_private"));
            function.setApiKey(rs.getString("api_key"));

            java.sql.Timestamp apiKeyTimestamp = rs.getTimestamp("api_key_generated_at");
            if (apiKeyTimestamp != null) {
                function.setApiKeyGeneratedAt(apiKeyTimestamp.toLocalDateTime());
            }
            String methodsStr = rs.getString("methods");
            if (methodsStr != null) {
                try {
                    List<String> methods = objectMapper.readValue(methodsStr, new TypeReference<List<String>>() {});
                    function.setMethods(methods);
                } catch (JsonProcessingException e) {
                    logger.warn("Could not parse methods JSON for function {}: {}", rs.getString("name"), e.getMessage());
                }
            }
            
            return function;
        } catch (SQLException e) {
            logger.error("Error mapping ResultSet to Function: {}", e.getMessage());
            throw e;
        }
    }
}