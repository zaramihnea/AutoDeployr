package com.infrastructure.exceptions;

/**
 * Exception for errors during code analysis/parsing
 */
public class CodeAnalysisException extends InfrastructureException {
    private final String language;
    private final String filePath;

    public CodeAnalysisException(String message) {
        super(message, "CODE_ANALYSIS_ERROR");
        this.language = "unknown";
        this.filePath = null;
    }

    public CodeAnalysisException(String message, Throwable cause) {
        super(message, "CODE_ANALYSIS_ERROR", cause);
        this.language = "unknown";
        this.filePath = null;
    }

    public CodeAnalysisException(String language, String message) {
        super("Error analyzing " + language + " code: " + message, "CODE_ANALYSIS_ERROR");
        this.language = language;
        this.filePath = null;
    }

    public CodeAnalysisException(String language, String message, Throwable cause) {
        super("Error analyzing " + language + " code: " + message, "CODE_ANALYSIS_ERROR", cause);
        this.language = language;
        this.filePath = null;
    }

    public CodeAnalysisException(String language, String filePath, String message) {
        super("Error analyzing " + language + " code in " + filePath + ": " + message, "CODE_ANALYSIS_ERROR");
        this.language = language;
        this.filePath = filePath;
    }

    public CodeAnalysisException(String language, String filePath, String message, Throwable cause) {
        super("Error analyzing " + language + " code in " + filePath + ": " + message, "CODE_ANALYSIS_ERROR", cause);
        this.language = language;
        this.filePath = filePath;
    }

    /**
     * Get the programming language being analyzed
     *
     * @return Programming language identifier
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Get the file path being analyzed
     *
     * @return File path or null if not applicable
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Create a parser error exception
     *
     * @param language Programming language
     * @param filePath File path
     * @param lineNumber Line number where the error occurred
     * @param message Error message
     * @return New exception
     */
    public static CodeAnalysisException parserError(String language, String filePath, int lineNumber, String message) {
        return new CodeAnalysisException(language, filePath,
                "Parser error at line " + lineNumber + ": " + message);
    }

    /**
     * Create a syntax error exception
     *
     * @param language Programming language
     * @param filePath File path
     * @param lineNumber Line number where the error occurred
     * @param message Error message
     * @return New exception
     */
    public static CodeAnalysisException syntaxError(String language, String filePath, int lineNumber, String message) {
        return new CodeAnalysisException(language, filePath,
                "Syntax error at line " + lineNumber + ": " + message);
    }

    /**
     * Create an unsupported language exception
     *
     * @param language Programming language
     * @return New exception
     */
    public static CodeAnalysisException unsupportedLanguage(String language) {
        return new CodeAnalysisException("Unsupported programming language: " + language);
    }

    /**
     * Create an unsupported framework exception
     *
     * @param language Programming language
     * @param framework Framework name
     * @return New exception
     */
    public static CodeAnalysisException unsupportedFramework(String language, String framework) {
        return new CodeAnalysisException(language, "Unsupported framework: " + framework);
    }
}