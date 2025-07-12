package com.application.exceptions;

public class CommandException extends ApplicationException {
    public CommandException(String command, String message) {
        super("Error executing command '" + command + "': " + message,
                "COMMAND_EXECUTION_ERROR", 500); // Fixed: passing 500 as Integer
    }

    public CommandException(String command, String message, Throwable cause) {
        super("Error executing command '" + command + "': " + message,
                "COMMAND_EXECUTION_ERROR", 500, cause); // Fixed: passing 500 as Integer
    }

    public CommandException(String message) {
        super(message, "COMMAND_EXECUTION_ERROR", 500); // Fixed: passing 500 as Integer
    }

    public CommandException(String message, Throwable cause) {
        super(message, "COMMAND_EXECUTION_ERROR", 500, cause); // Fixed: passing 500 as Integer
    }

}