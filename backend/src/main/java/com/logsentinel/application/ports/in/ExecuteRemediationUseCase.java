package com.logsentinel.application.ports.in;

import com.logsentinel.domain.model.RemediationAction;

import java.util.UUID;

/**
 * Driving port (use case) for executing an AI-suggested remediation script
 * against a given incident under strict sandboxed isolation, and auditing the
 * outcome (LOG-US4-BE-02). Pure Java interface — no framework dependency.
 */
public interface ExecuteRemediationUseCase {

    /**
     * Command record encapsulating the data needed to execute a remediation script.
     * <p>
     * Carries only {@code incidentId}: the script itself is never supplied by the
     * caller (LOG-US3-DB-02B, design decision Option B, approved 2026-08-11) — the
     * implementation resolves {@code generatedScript} authoritatively from the
     * {@code suggestedScript} of the incident's persisted {@code IncidentDiagnostic}.
     */
    record ExecuteRemediationCommand(UUID incidentId) {
    }

    /**
     * Executes the remediation use case: resolves the script to run from the
     * incident's persisted diagnostic, commits an immediate audit record in
     * {@code EXECUTING} status, runs the script in isolation, and commits the
     * closure ({@code SUCCESS}/{@code FAILED}) once it concludes.
     *
     * @param command the incident being remediated
     * @return the closed remediation action, reflecting its final outcome
     * @throws com.logsentinel.domain.exception.RemediationScriptUnavailableException
     *         if no diagnostic is persisted for the incident, or its
     *         {@code suggestedScript} is {@code null}
     */
    RemediationAction execute(ExecuteRemediationCommand command);
}
