package com.logsentinel.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain entity representing the frozen, audit-grade consolidated diagnostic text
 * produced by the AI for a single incident (LOG-US3-DB-02). Persisted exactly once,
 * when the SSE streaming channel of {@code StreamDiagnosticService} closes
 * successfully — one-to-one with its {@link Incident}. Pure domain object — NO
 * framework dependency (no JPA, no Spring).
 * <p>
 * {@code suggestedScript} (LOG-US3-DB-02B, design decision Option B, approved
 * 2026-08-11) is the remediation script the backend derives — authoritatively,
 * exactly once — from {@code diagnosticText} via
 * {@code com.logsentinel.domain.service.SuggestedScriptExtractor}. It is {@code null}
 * whenever the AI's diagnostic did not contain a parseable fenced code block; the
 * client never supplies executable code in the remediation flow.
 */
public class IncidentDiagnostic {

    private final UUID id;
    private final UUID incidentId;
    private final String diagnosticText;
    private final String suggestedScript;
    private final OffsetDateTime createdAt;

    public IncidentDiagnostic(UUID id, UUID incidentId, String diagnosticText, String suggestedScript,
                               OffsetDateTime createdAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.diagnosticText = diagnosticText;
        this.suggestedScript = suggestedScript;
        this.createdAt = createdAt;
    }

    /**
     * Factory method for a diagnostic about to be persisted for the first time.
     * ID and timestamp are deferred to the persistence layer.
     *
     * @param suggestedScript the script derived from {@code diagnosticText} by
     *                        {@code SuggestedScriptExtractor}, or {@code null} when no
     *                        parseable fenced code block was found
     */
    public static IncidentDiagnostic createNew(UUID incidentId, String diagnosticText, String suggestedScript) {
        return new IncidentDiagnostic(null, incidentId, diagnosticText, suggestedScript, null);
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getDiagnosticText() {
        return diagnosticText;
    }

    public String getSuggestedScript() {
        return suggestedScript;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
