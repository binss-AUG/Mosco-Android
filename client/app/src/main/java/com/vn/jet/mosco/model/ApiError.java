package com.vn.jet.mosco.model;

/**
 * Parsed error response from the server.
 * Used for standardized error handling across API calls.
 */
public class ApiError {

    private String error;
    private String message;

    public ApiError() {}

    public ApiError(String error, String message) {
        this.error = error;
        this.message = message;
    }

    /**
     * Returns the most descriptive error message available.
     */
    public String getDisplayMessage() {
        if (message != null && !message.isEmpty()) return message;
        if (error != null && !error.isEmpty()) return error;
        return "Unknown error";
    }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
