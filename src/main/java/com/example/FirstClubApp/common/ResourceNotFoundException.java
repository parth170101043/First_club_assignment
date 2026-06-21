package com.example.FirstClubApp.common;

/**
 * Represents a requested membership resource that does not exist.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates a not-found exception with a client-safe explanation.
     *
     * @param message description of the missing resource; no default value is applied
     * @return a new resource-not-found exception
     * @implNote Used by domain services and translated to HTTP 404 by {@link ApiExceptionHandler}.
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
