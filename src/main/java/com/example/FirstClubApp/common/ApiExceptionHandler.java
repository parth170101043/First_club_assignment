package com.example.FirstClubApp.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converts application and validation exceptions into consistent RFC 9457 problem responses.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * Converts a missing-resource failure into an HTTP 404 problem response.
     *
     * @param exception missing-resource exception raised by a service
     * @return problem details with HTTP 404 status
     * @implNote Used by Spring MVC whenever a controller call throws the supplied exception.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage());
    }

    /**
     * Converts a domain conflict into an HTTP 409 problem response.
     *
     * @param exception conflict exception raised by a service
     * @return problem details with HTTP 409 status
     * @implNote Used by Spring MVC whenever a controller call throws the supplied exception.
     */
    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Request conflict", exception.getMessage());
    }

    /**
     * Converts a database constraint failure into a safe HTTP 409 response.
     *
     * @param exception persistence exception raised when a database constraint is violated
     * @return problem details with HTTP 409 status
     * @implNote Used by Spring MVC as a final safeguard for concurrent or duplicate writes.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConstraintViolation(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "Database constraint violation",
            "The request conflicts with existing data.");
    }

    /**
     * Converts a stale concurrent entity update into a retryable HTTP 409 response.
     *
     * @param exception optimistic-lock failure raised after another transaction changed the entity
     * @return problem details with HTTP 409 status and retry guidance
     * @implNote Used by Spring MVC for concurrent order-driven tier evaluations and other versioned writes.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "Concurrent update conflict",
            "The membership changed concurrently. Refresh the latest state and retry.");
    }

    /**
     * Converts request-bean validation failures into an HTTP 400 response with field errors.
     *
     * @param exception validation exception containing rejected request fields
     * @return problem details with HTTP 400 status and an {@code errors} property
     * @implNote Used by Spring MVC when a {@code @Valid} controller argument is invalid.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Validation failed",
            "One or more request fields are invalid.");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
            .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        detail.setProperty("errors", errors);
        return detail;
    }

    /**
     * Creates the shared problem-details representation.
     *
     * @param status HTTP status assigned to the response
     * @param title short category describing the failure
     * @param message client-safe explanation of the failure
     * @return initialized problem details with the supplied status, title, and message
     * @implNote Used internally by all exception-handler methods in this class.
     */
    private ProblemDetail problem(HttpStatus status, String title, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(title);
        detail.setType(URI.create("about:blank"));
        return detail;
    }
}
