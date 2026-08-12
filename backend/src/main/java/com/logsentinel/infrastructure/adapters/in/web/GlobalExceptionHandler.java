package com.logsentinel.infrastructure.adapters.in.web;

import com.logsentinel.domain.exception.IncidentNotFoundException;
import com.logsentinel.domain.exception.RemediationScriptUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler that intercepts validation failures and other errors,
 * returning clean HTTP responses WITHOUT exposing internal stacktraces.
 *
 * All algorithms here are O(n) — linear iteration over field errors only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles JSR-380 validation failures from @Valid annotations.
     * Returns HTTP 400 with structured field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        // O(n) linear scan over field errors — no backtracking
        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() != null
                                ? error.getDefaultMessage()
                                : "invalid value"
                ))
                .toList();

        Map<String, Object> body = Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Validation Failed",
                "fieldErrors", fieldErrors
        );

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles malformed JSON or unreadable request bodies (e.g., invalid enum values).
     * Returns HTTP 400 without exposing internal parsing details.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(
            HttpMessageNotReadableException ex) {

        Map<String, Object> body = Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Malformed Request",
                "message", "Request body is missing or contains invalid data"
        );

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles a lookup for an incident that does not exist. Also covers the SSE
     * diagnostic stream (LOG-US3-BE-01): when the incident is not found, the async
     * worker never writes any data before failing, so {@code SseEmitter.completeWithError}
     * re-dispatches the request through Spring's normal MVC exception handling
     * (no bytes committed yet), landing here with a clean HTTP 404 instead of a 500.
     * Returns HTTP 404 without exposing internal details.
     */
    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleIncidentNotFound(IncidentNotFoundException ex) {

        Map<String, Object> body = Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Not Found",
                "message", "Incident not found"
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Handles {@code POST /incidents/{id}/remediations} (LOG-US4-BE-02) when there is
     * no script to execute for the incident: either no diagnostic was ever persisted
     * for it, or its {@code suggestedScript} is {@code null} (LOG-US3-DB-02B, design
     * decision Option B) — no {@code remediation_actions} row is created in either
     * case. Returns HTTP 409 Conflict without exposing internal details.
     */
    @ExceptionHandler(RemediationScriptUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleRemediationScriptUnavailable(
            RemediationScriptUnavailableException ex) {

        Map<String, Object> body = Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", HttpStatus.CONFLICT.value(),
                "error", "Conflict",
                "message", "No remediation script available for this incident"
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * NEVER exposes stacktraces to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {

        Map<String, Object> body = Map.of(
                "timestamp", OffsetDateTime.now().toString(),
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Internal Server Error",
                "message", "An unexpected error occurred"
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
