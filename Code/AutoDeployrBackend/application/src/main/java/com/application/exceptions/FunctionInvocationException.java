package com.application.exceptions;

public class FunctionInvocationException extends CommandException {
    public FunctionInvocationException(String functionName, String message) {
        super("Error invoking function '" + functionName + "': " + message);
    }

    public FunctionInvocationException(String functionName, String message, Throwable cause) {
        super("Error invoking function '" + functionName + "': " + message, cause);
    }
}
