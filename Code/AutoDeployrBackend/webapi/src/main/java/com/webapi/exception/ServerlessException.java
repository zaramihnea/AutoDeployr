package com.webapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception for serverless platform-specific errors
 */
public class ServerlessException extends APIException {

    /**
     * Create a new serverless platform exception
     *
     * @param errorCode Error code for the specific error type
     * @param message Error message
     */
    public ServerlessException(String errorCode, String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, message);
    }

    /**
     * Create a new serverless platform exception with a cause
     *
     * @param errorCode Error code for the specific error type
     * @param message Error message
     * @param cause Original exception
     */
    public ServerlessException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, message, cause);
    }

    /**
     * Create a deployment exception
     *
     * @param message Error message
     * @return New deployment exception
     */
    public static ServerlessException deploymentError(String message) {
        return new ServerlessException("DEPLOYMENT_ERROR", message);
    }

    /**
     * Create a function execution exception
     *
     * @param functionName Name of the function that failed
     * @param message Error message
     * @return New function execution exception
     */
    public static ServerlessException executionError(String functionName, String message) {
        return new ServerlessException(
                "EXECUTION_ERROR",
                String.format("Error executing function '%s': %s", functionName, message)
        );
    }

    /**
     * Create a Docker service exception
     *
     * @param message Error message
     * @return New Docker service exception
     */
    public static ServerlessException dockerError(String message) {
        return new ServerlessException("DOCKER_ERROR", message);
    }

    /**
     * Create a code analysis exception
     *
     * @param message Error message
     * @return New code analysis exception
     */
    public static ServerlessException analysisError(String message) {
        return new ServerlessException("ANALYSIS_ERROR", message);
    }
}