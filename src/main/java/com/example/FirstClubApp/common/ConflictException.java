package com.example.FirstClubApp.common;

/**
 * Represents a request that conflicts with the current membership state or existing data.
 */
public class ConflictException extends RuntimeException {

    /**
     * Creates a conflict exception with a client-safe explanation.
     *
     * @param message description of the conflicting state; no default value is applied
     * @return a new conflict exception
     * @implNote Used by domain services and translated to HTTP 409 by {@link ApiExceptionHandler}.
     */
    public ConflictException(String message) {
        super(message);
    }
}
