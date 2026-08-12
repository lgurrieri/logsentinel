package com.logsentinel.application.ports.in;

import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.IncidentDiagnostic;

import java.util.List;
import java.util.UUID;

/**
 * Driving port (use case) for reading the consolidated detail of a single incident
 * (LOG-US4-BE-03) — its own data plus the full history of AI-generated diagnostics
 * persisted for it (LOG-US3-DB-02 / LOG-US3-DB-02B), each carrying its
 * {@code suggestedScript} if the AI's diagnostic produced one. Pure Java interface —
 * no framework dependency.
 */
public interface GetIncidentDetailUseCase {

    /**
     * Result record combining the incident with its persisted diagnostic history.
     * {@code analyses} is empty when no diagnostic was ever persisted for the
     * incident (LOG-US3-DB-02 currently persists at most one diagnostic per
     * incident, enforced one-to-one at the database level).
     */
    record IncidentDetailResult(Incident incident, List<IncidentDiagnostic> analyses) {
    }

    /**
     * Executes the incident detail lookup.
     *
     * @param incidentId the incident id
     * @return the incident with its diagnostic history
     * @throws com.logsentinel.domain.exception.IncidentNotFoundException if no
     *         incident with that id exists
     */
    IncidentDetailResult execute(UUID incidentId);
}
