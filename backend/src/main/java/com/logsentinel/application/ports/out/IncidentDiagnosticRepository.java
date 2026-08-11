package com.logsentinel.application.ports.out;

import com.logsentinel.domain.model.IncidentDiagnostic;

import java.util.Optional;
import java.util.UUID;

/**
 * Driven port (SPI) for persisting the frozen, consolidated AI diagnostic of an
 * incident (LOG-US3-DB-02), one-to-one with its {@code Incident}. Pure Java interface
 * — implemented by the persistence adapter in infrastructure.
 */
public interface IncidentDiagnosticRepository {

    /**
     * Persists a new incident diagnostic and returns it with generated id and
     * timestamp. Called exactly once per incident, when the SSE diagnostic stream
     * closes successfully (see {@code StreamDiagnosticService}).
     *
     * @param diagnostic the incident diagnostic domain object to persist
     * @return the persisted diagnostic with id and createdAt populated
     */
    IncidentDiagnostic save(IncidentDiagnostic diagnostic);

    /**
     * Looks up the single diagnostic persisted for a given incident (one-to-one
     * relationship). Used by {@code ExecuteRemediationService} (LOG-US4-BE-02) to
     * resolve the {@code generatedScript} to run from {@code suggestedScript}
     * (LOG-US3-DB-02B, Option B) — the client never supplies executable code.
     *
     * @param incidentId the incident id
     * @return the persisted diagnostic, or empty if none was ever persisted for it
     */
    Optional<IncidentDiagnostic> findByIncidentId(UUID incidentId);
}
