package com.infrastructure.exceptions;

/**
 * Exception for errors during template processing and code generation
 */
public class TemplateException extends InfrastructureException {
    public TemplateException(String message) {
        super(message, "TEMPLATE_PROCESSING_ERROR");
    }

    public TemplateException(String message, Throwable cause) {
        super(message, "TEMPLATE_PROCESSING_ERROR", cause);
    }

    public TemplateException(String templateName, String message) {
        super("Template '" + templateName + "' processing failed: " + message, "TEMPLATE_PROCESSING_ERROR");
    }

    public TemplateException(String templateName, String message, Throwable cause) {
        super("Template '" + templateName + "' processing failed: " + message, "TEMPLATE_PROCESSING_ERROR", cause);
    }
}