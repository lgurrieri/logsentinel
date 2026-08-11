package com.logsentinel.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain entity representing the frozen, audit-grade consolidated diagnostic text
 * produced by the AI for a single incident (LOG-US3-DB-02). Persisted exactly once,
 * when the SSE streaming channel of {@code StreamDiagnosticService} closes
 * successfully — one-to-one with its {@link Incident}. Pure domain object — NO
 * framework dependency (no JPA, no Spring).
 */
public class IncidentDiagnostic {

    private final UUID id;
    private final UUID incidentId;
    private final String diagnosticText;
    private final OffsetDateTime createdAt;

    public IncidentDiagnostic(UUID id, UUID incidentId, String diagnosticText, OffsetDateTime createdAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.diagnosticText = diagnosticText;
        this.createdAt = createdAt;
    }

    /**
     * Factory method for a diagnostic about to be persisted for the first time.
     * ID and timestamp are deferred to the persistence layer.
     */
    public static IncidentDiagnostic createNew(UUID incidentId, String diagnosticText) {
        return new IncidentDiagnostic(null, incidentId, diagnosticText, null);
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
