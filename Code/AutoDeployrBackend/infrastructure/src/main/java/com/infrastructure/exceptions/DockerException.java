package com.infrastructure.exceptions;

/**
 * Exception for Docker service errors
 */
public class DockerException extends InfrastructureException {
    private final String operation;
    private final String containerOrImageId;

    public DockerException(String message) {
        super(message, "DOCKER_ERROR");
        this.operation = null;
        this.containerOrImageId = null;
    }

    public DockerException(String message, Throwable cause) {
        super(message, "DOCKER_ERROR", cause);
        this.operation = null;
        this.containerOrImageId = null;
    }

    public DockerException(String operation, String message) {
        super("Docker operation '" + operation + "' failed: " + message, "DOCKER_ERROR");
        this.operation = operation;
        this.containerOrImageId = null;
    }

    public DockerException(String operation, String message, Throwable cause) {
        super("Docker operation '" + operation + "' failed: " + message, "DOCKER_ERROR", cause);
        this.operation = operation;
        this.containerOrImageId = null;
    }

    public DockerException(String operation, String containerOrImageId, String message) {
        super("Docker operation '" + operation + "' failed for " + containerOrImageId + ": " + message, "DOCKER_ERROR");
        this.operation = operation;
        this.containerOrImageId = containerOrImageId;
    }

    public DockerException(String operation, String containerOrImageId, String message, Throwable cause) {
        super("Docker operation '" + operation + "' failed for " + containerOrImageId + ": " + message, "DOCKER_ERROR", cause);
        this.operation = operation;
        this.containerOrImageId = containerOrImageId;
    }

    /**
     * Get the Docker operation that failed
     *
     * @return Operation name or null if not applicable
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Get the container or image ID that was involved
     *
     * @return Container or image ID or null if not applicable
     */
    public String getContainerOrImageId() {
        return containerOrImageId;
    }

    /**
     * Create a connection error exception
     *
     * @param message Error message
     * @return New exception
     */
    public static DockerException connectionError(String message) {
        return new DockerException("connect", "Connection to Docker daemon failed: " + message);
    }

    /**
     * Create a build error exception
     *
     * @param imageId Image ID or name
     * @param message Error message
     * @return New exception
     */
    public static DockerException buildError(String imageId, String message) {
        return new DockerException("build", imageId, message);
    }

    /**
     * Create a container execution error exception
     *
     * @param containerId Container ID
     * @param message Error message
     * @return New exception
     */
    public static DockerException executionError(String containerId, String message) {
        return new DockerException("execute", containerId, message);
    }

    /**
     * Create a timeout exception
     *
     * @param operation Operation that timed out
     * @param containerId Container ID or null if not applicable
     * @return New exception
     */
    public static DockerException timeout(String operation, String containerId) {
        if (containerId != null) {
            return new DockerException(operation, containerId, "Operation timed out");
        } else {
            return new DockerException(operation, "Operation timed out");
        }
    }
}