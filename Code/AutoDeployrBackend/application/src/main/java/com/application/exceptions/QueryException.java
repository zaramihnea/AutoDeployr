package com.application.exceptions;

public class QueryException extends ApplicationException {
    public QueryException(String query, String message) {
        super("Error executing query '" + query + "': " + message,
                "QUERY_EXECUTION_ERROR", 500);
    }

    public QueryException(String query, String message, Throwable cause) {
        super("Error executing query '" + query + "': " + message,
                "QUERY_EXECUTION_ERROR", 500, cause);
    }

    public QueryException(String message) {
        super(message, "QUERY_EXECUTION_ERROR", 500);
    }

    public QueryException(String message, Throwable cause) {
        super(message, "QUERY_EXECUTION_ERROR", 500, cause);
    }
}
