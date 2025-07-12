package com.infrastructure.exceptions;

/**
 * Exception for file system operations, including read, write, delete, etc.
 */
public class FileOperationException extends InfrastructureException {
    public FileOperationException(String message) {
        super(message, "FILE_OPERATION_ERROR");
    }

    public FileOperationException(String message, Throwable cause) {
        super(message, "FILE_OPERATION_ERROR", cause);
    }

    public FileOperationException(String operation, String filePath, String message) {
        super("File operation '" + operation + "' failed for " + filePath + ": " + message, "FILE_OPERATION_ERROR");
    }

    public FileOperationException(String operation, String filePath, String message, Throwable cause) {
        super("File operation '" + operation + "' failed for " + filePath + ": " + message, "FILE_OPERATION_ERROR", cause);
    }

    /**
     * Create a file not found exception
     *
     * @param filePath Path to the file
     * @return FileOperationException with appropriate message
     */
    public static FileOperationException fileNotFound(String filePath) {
        return new FileOperationException("read", filePath, "File not found");
    }

    /**
     * Create a file access denied exception
     *
     * @param filePath Path to the file
     * @return FileOperationException with appropriate message
     */
    public static FileOperationException accessDenied(String filePath) {
        return new FileOperationException("access", filePath, "Access denied");
    }

    /**
     * Create a file creation failed exception
     *
     * @param filePath Path to the file
     * @return FileOperationException with appropriate message
     */
    public static FileOperationException creationFailed(String filePath) {
        return new FileOperationException("create", filePath, "Failed to create file");
    }
}