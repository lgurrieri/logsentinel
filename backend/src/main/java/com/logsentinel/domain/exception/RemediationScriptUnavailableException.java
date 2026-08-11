package com.logsentinel.domain.exception;

import java.util.UUID;

/**
 * Thrown when {@code POST /incidents/{id}/remediations} (LOG-US4-BE-02) cannot
 * determine which script to execute for a given incident: either no
 * {@code IncidentDiagnostic} has been persisted yet for that incident (the SSE
 * diagnostic stream never closed successfully, see {@code StreamDiagnosticService}),
 * or the persisted diagnostic exists but its {@code suggestedScript} is
 * {@code null} (the AI's diagnostic text did not contain a parseable fenced code
 * block, see {@code SuggestedScriptExtractor} — LOG-US3-DB-02B, design decision
 * Option B, approved 2026-08-11).
 * <p>
 * Maps to HTTP 409 Conflict (see {@code GlobalExceptionHandler}): no
 * {@code remediation_actions} row is ever created for this request. Pure domain
 * exception — no framework dependency.
 */
public class RemediationScriptUnavailableException extends RuntimeException {

    public RemediationScriptUnavailableException(UUID incidentId) {
        super("No remediation script available for incident: " + incidentId);
    }
}
