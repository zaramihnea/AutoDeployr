package com.application.exceptions;

public class DeploymentException extends CommandException {
    public DeploymentException(String message) {
        super("Deployment failed: " + message);
    }

    public DeploymentException(String message, Throwable cause) {
        super("Deployment failed: " + message, cause);
    }
}