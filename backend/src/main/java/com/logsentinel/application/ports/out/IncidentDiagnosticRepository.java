package com.logsentinel.application.ports.out;

import com.logsentinel.domain.model.IncidentDiagnostic;

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
}
