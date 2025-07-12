package com.infrastructure.services.deploy;

import com.application.dtos.request.GitHubDeployRequest;
import com.application.dtos.response.DeploymentResponse;
import com.application.usecases.commandhandlers.DeployApplicationCommandHandler;
import com.application.usecases.commands.DeployApplicationCommand;
import com.domain.exceptions.ValidationException;
import com.infrastructure.exceptions.GitOperationException;
import com.infrastructure.services.config.EnvironmentVariableService;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

/**
 * Service for cloning and deploying GitHub repositories
 */
@Service
@RequiredArgsConstructor
public class GitHubService {
    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);

    private final DeployApplicationCommandHandler deployApplicationCommandHandler;
    private final EnvironmentVariableService environmentVariableService;

    /**
     * Clone a GitHub repository and deploy it
     *
     * @param request GitHub repository deployment request
     * @param userId User ID
     * @return Deployment response
     * @throws GitOperationException If Git operations fail
     * @throws ValidationException If validation fails
     */
    public DeploymentResponse cloneAndDeploy(GitHubDeployRequest request, String userId) {
        Path tempDir = null;
        try {
            // Validate request
            request.validate();

            // Extract app name first - either from custom name or from repository URL
            String appName = request.getCustomAppName();
            if (appName == null || appName.isBlank()) {
                appName = extractAppNameFromUrl(request.getRepositoryUrl());
            } else {
                appName = appName.replaceAll("[^a-zA-Z0-9-_]", "_");
            }

            // Log the app name we're using
            logger.info("Using app name: {} for repository: {}", appName, request.getRepositoryUrl());

            // Create base directory for git clones
            Path baseDir = Path.of(System.getProperty("user.dir"), "git-clones");
            Files.createDirectories(baseDir);

            // Create directory for this specific app (NO random elements)
            Path appDir = baseDir.resolve(appName);
            if (Files.exists(appDir)) {
                cleanupTempDirectory(appDir);
            }
            Files.createDirectories(appDir);
            tempDir = appDir;

            logger.info("Cloning repository: {} (branch: {}) to: {} with app name: {}",
                    request.getRepositoryUrl(), request.getBranch(), tempDir, appName);

            // Clone the repository
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(request.getRepositoryUrl())
                    .setBranch(request.getBranch())
                    .setDirectory(tempDir.toFile());

            // Add credentials for private repositories if provided
            if (request.getUsername() != null && !request.getUsername().isEmpty() &&
                    request.getToken() != null && !request.getToken().isEmpty()) {

                cloneCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(request.getUsername(), request.getToken())
                );
            }
            try (Git git = cloneCommand.call()) {
                logger.info("Successfully cloned repository to: {}", tempDir);
            }

            // Get environment variables from the request
            Map<String, String> envVars = request.getEnvironmentVariables() != null ?
                    request.getEnvironmentVariables() :
                    Map.of();

            // Store environment variables securely in the database using the app name
            if (!envVars.isEmpty()) {
                logger.info("Storing env vars with appName='{}', userId='{}'", appName, userId);
                int storedCount = environmentVariableService.storeEnvironmentVariables(
                        appName,
                        userId,
                        envVars
                );
                logger.info("Securely stored {} environment variables for app: {}", storedCount, appName);
            } else {
                logger.info("No environment variables provided for app: {}", appName);
            }

            // Deploy the application using the existing command handler
            DeployApplicationCommand command = DeployApplicationCommand.builder()
                    .appPath(tempDir.toString())
                    .environmentVariables(envVars)
                    .userId(userId)
                    .isPrivate(request.isPrivate())
                    .build();

            logger.info("Deploying application from path: {} for user: {} with {} environment variables, app name: {}, private: {}",
                    tempDir, userId, envVars.size(), appName, request.isPrivate());

            DeploymentResponse response = deployApplicationCommandHandler.handle(command);
            if (!"success".equals(response.getStatus()) && !"partial".equals(response.getStatus())) {
                cleanupTempDirectory(tempDir);
            }

            return response;

        } catch (GitAPIException e) {
            logger.error("Git API error: {}", e.getMessage(), e);
            cleanupTempDirectory(tempDir);
            throw new GitOperationException("Failed to clone repository: " + e.getMessage(), e);
        } catch (IOException e) {
            logger.error("I/O error: {}", e.getMessage(), e);
            cleanupTempDirectory(tempDir);
            throw new GitOperationException("Failed to create/access temporary directory: " + e.getMessage(), e);
        } catch (ValidationException e) {
            logger.warn("Validation failed for GitHub deployment: {}", e.getMessage());
            cleanupTempDirectory(tempDir);
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during GitHub deployment: {}", e.getMessage(), e);
            cleanupTempDirectory(tempDir);
            throw new GitOperationException("Unexpected error during GitHub deployment: " + e.getMessage(), e);
        }
    }

    /**
     * Extract application name from GitHub repository URL with improved parsing
     */
    private String extractAppNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            logger.warn("Empty repository URL provided, using default name");
            return "github_repo";
        }

        String repoName;
        String originalUrl = url;

        try {
            if (url.endsWith(".git")) {
                url = url.substring(0, url.length() - 4);
            }
            url = url.replaceAll("/+$", "");
            if (url.contains("github.com")) {
                if (url.contains("github.com/")) {
                    // HTTPS: https://github.com/username/repo
                    String[] parts = url.split("github\\.com/", 2);
                    if (parts.length == 2 && parts[1].contains("/")) {
                        String[] pathParts = parts[1].split("/");
                        if (pathParts.length >= 2) {
                            repoName = pathParts[1];
                            logger.debug("Extracted repo name '{}' from HTTPS URL path components: {}",
                                    repoName, Arrays.toString(pathParts));
                        } else {
                            repoName = pathParts[0];
                            logger.debug("Falling back to first path component '{}' for HTTPS URL", repoName);
                        }
                    } else {
                        repoName = url.substring(url.lastIndexOf('/') + 1);
                        logger.debug("Extracted repo name '{}' from last URL component", repoName);
                    }
                } else if (url.contains("github.com:")) {
                    String[] parts = url.split("github\\.com:", 2);
                    if (parts.length == 2 && parts[1].contains("/")) {
                        String[] pathParts = parts[1].split("/");
                        if (pathParts.length >= 2) {
                            repoName = pathParts[1];
                            logger.debug("Extracted repo name '{}' from SSH URL path components: {}",
                                    repoName, Arrays.toString(pathParts));
                        } else {
                            repoName = pathParts[0];
                            logger.debug("Falling back to first path component '{}' for SSH URL", repoName);
                        }
                    } else {
                        repoName = parts[1];
                        logger.debug("Extracted repo name '{}' from SSH URL after colon", repoName);
                    }
                } else {
                    repoName = url.substring(url.lastIndexOf('/') + 1);
                    logger.debug("Extracted repo name '{}' from last URL component (unknown GitHub URL format)", repoName);
                }
            } else {
                if (url.contains("/")) {
                    repoName = url.substring(url.lastIndexOf('/') + 1);
                    logger.debug("Extracted repo name '{}' from non-GitHub URL last component", repoName);
                } else {
                    repoName = url;
                    logger.debug("Using entire URL '{}' as repo name (no path components found)", repoName);
                }
            }
        } catch (Exception e) {
            logger.warn("Error extracting repo name from URL '{}': {}. Using URL hash.", originalUrl, e.getMessage());
            repoName = "repo_" + Math.abs(originalUrl.hashCode() % 1000);
        }
        if (repoName == null || repoName.isEmpty()) {
            logger.warn("Extracted empty repo name from URL '{}', using fallback", originalUrl);
            repoName = "github_repo";
        } else {
            String sanitized = repoName.replaceAll("[^a-zA-Z0-9-_]", "_");
            if (!sanitized.equals(repoName)) {
                logger.debug("Sanitized repo name from '{}' to '{}'", repoName, sanitized);
                repoName = sanitized;
            }
        }

        logger.info("Final repository name extracted from URL '{}': '{}'", originalUrl, repoName);
        return repoName;
    }

    /**
     * Clean up a temporary directory after deployment
     */
    private void cleanupTempDirectory(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            logger.debug("Temporary directory cleanup skipped: Directory is null or does not exist ({})", tempDir);
            return;
        }

        logger.info("Attempting to clean up temporary directory: {}", tempDir);
        try {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            logger.debug("Deleted: {}", path);
                        } catch (IOException e) {
                            logger.warn("Failed to delete path: {}. Error: {}. Scheduling deletion on exit.", path, e.getMessage());
                            path.toFile().deleteOnExit();
                        }
                    });
            if (!Files.exists(tempDir)) {
                logger.info("Successfully cleaned up temporary directory: {}", tempDir);
            } else {
                logger.warn("Temporary directory might not be fully cleaned up (possibly due to open file handles or permissions): {}", tempDir);
                tempDir.toFile().deleteOnExit();
            }

        } catch (IOException e) {
            logger.error("Error during cleanup process for temporary directory {}: {}", tempDir, e.getMessage(), e);
            tempDir.toFile().deleteOnExit();
        }
    }
}