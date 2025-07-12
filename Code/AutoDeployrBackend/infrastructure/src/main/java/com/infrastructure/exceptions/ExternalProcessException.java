package com.infrastructure.exceptions;

/**
 * Exception for external process execution errors
 */
public class ExternalProcessException extends InfrastructureException {
    private final String command;
    private final Integer exitCode;

    public ExternalProcessException(String message) {
        super(message, "EXTERNAL_PROCESS_ERROR");
        this.command = null;
        this.exitCode = null;
    }

    public ExternalProcessException(String message, Throwable cause) {
        super(message, "EXTERNAL_PROCESS_ERROR", cause);
        this.command = null;
        this.exitCode = null;
    }

    public ExternalProcessException(String command, String message) {
        super("External command '" + command + "' failed: " + message, "EXTERNAL_PROCESS_ERROR");
        this.command = command;
        this.exitCode = null;
    }

    public ExternalProcessException(String command, String message, Throwable cause) {
        super("External command '" + command + "' failed: " + message, "EXTERNAL_PROCESS_ERROR", cause);
        this.command = command;
        this.exitCode = null;
    }

    public ExternalProcessException(String command, int exitCode, String message) {
        super("External command '" + command + "' failed with exit code " + exitCode + ": " + message,
                "EXTERNAL_PROCESS_ERROR");
        this.command = command;
        this.exitCode = exitCode;
    }

    /**
     * Get the command that failed
     *
     * @return Command string or null if not applicable
     */
    public String getCommand() {
        return command;
    }

    /**
     * Get the exit code of the failed process
     *
     * @return Exit code or null if not applicable
     */
    public Integer getExitCode() {
        return exitCode;
    }

    /**
     * Create a command not found exception
     *
     * @param command Command that was not found
     * @return New exception
     */
    public static ExternalProcessException commandNotFound(String command) {
        return new ExternalProcessException(command, "Command not found");
    }

    /**
     * Create a timeout exception
     *
     * @param command Command that timed out
     * @param timeout Timeout duration in seconds
     * @return New exception
     */
    public static ExternalProcessException timeout(String command, int timeout) {
        return new ExternalProcessException(command, "Process timed out after " + timeout + " seconds");
    }

    /**
     * Create an exit code exception
     *
     * @param command Command that returned non-zero exit code
     * @param exitCode Exit code
     * @param output Process output or error message
     * @return New exception
     */
    public static ExternalProcessException exitCode(String command, int exitCode, String output) {
        return new ExternalProcessException(command, exitCode,
                "Process exited with code " + exitCode + ": " + output);
    }
}