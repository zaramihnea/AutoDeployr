package com.infrastructure.exceptions;

/**
 * Exception for file upload operations
 */
public class FileUploadException extends RuntimeException {

    /**
     * Create a new file upload exception
     *
     * @param message Exception message
     */
    public FileUploadException(String message) {
        super(message);
    }

    /**
     * Create a new file upload exception
     *
     * @param message Exception message
     * @param cause Cause of the exception
     */
    public FileUploadException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new file upload exception for a specific operation
     *
     * @param operation Operation that failed
     * @param message Exception message
     * @return File upload exception
     */
    public static FileUploadException operationFailed(String operation, String message) {
        return new FileUploadException("File upload operation failed: " + operation + " - " + message);
    }

    /**
     * Create a new file upload exception for an unsupported file type
     *
     * @param fileType File type
     * @return File upload exception
     */
    public static FileUploadException unsupportedFileType(String fileType) {
        return new FileUploadException("Unsupported file type: " + fileType + ". Only .zip files are allowed.");
    }

    /**
     * Create a new file upload exception for an empty file
     *
     * @return File upload exception
     */
    public static FileUploadException emptyFile() {
        return new FileUploadException("Uploaded file is empty");
    }

    /**
     * Create a new file upload exception for a file that exceeds the maximum size
     *
     * @param maxSize Maximum size
     * @return File upload exception
     */
    public static FileUploadException fileTooLarge(String maxSize) {
        return new FileUploadException("Uploaded file exceeds the maximum allowed size of " + maxSize);
    }
}