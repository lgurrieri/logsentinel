package com.logsentinel.application.service;

import com.logsentinel.application.ports.in.ExecuteRemediationUseCase;
import com.logsentinel.application.ports.out.IncidentDiagnosticRepository;
import com.logsentinel.application.ports.out.SecuritySandbox;
import com.logsentinel.domain.exception.RemediationScriptUnavailableException;
import com.logsentinel.domain.model.IncidentDiagnostic;
import com.logsentinel.domain.model.RemediationAction;
import com.logsentinel.domain.model.SandboxExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Application service implementing {@link ExecuteRemediationUseCase} (LOG-US4-BE-02).
 * Orchestrates the full remediation flow WITHOUT itself being {@code @Transactional}:
 * <ol>
 *     <li>Resolves {@code generatedScript} from the incident's persisted
 *         {@link IncidentDiagnostic#getSuggestedScript()} (LOG-US3-DB-02B, design
 *         decision Option B, approved 2026-08-11) — never from the caller. If no
 *         diagnostic is persisted for the incident, or its {@code suggestedScript}
 *         is {@code null}, fails fast with {@link RemediationScriptUnavailableException}
 *         (mapped to HTTP 409 by {@code GlobalExceptionHandler}) before ever touching
 *         the state machine or the sandbox — no {@code remediation_actions} row is
 *         created.</li>
 *     <li>Transaction A: {@link RemediationStateMachine#commitExecuting} commits an
 *         audit record in {@code EXECUTING} immediately.</li>
 *     <li>Untransactional phase: the script runs in isolation via
 *         {@link SecuritySandbox#executeInIsolation} (LOG-US4-BE-01) — deliberately
 *         outside any DB transaction, so potentially slow external process I/O never
 *         holds a connection from the pool.</li>
 *     <li>Transaction B: {@link RemediationStateMachine#commitClosure} commits the
 *         final status once the isolated execution concludes (or was rejected by the
 *         sandbox before ever starting, e.g. a disallowed command) — the audit record
 *         is never left stuck in {@code EXECUTING}.</li>
 * </ol>
 */
@Service
public class ExecuteRemediationService implements ExecuteRemediationUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExecuteRemediationService.class);

    /**
     * Sentinel exit code used to close the audit trail as {@code FAILED} when the
     * sandbox rejects the script before ever spawning a subprocess (e.g. a command
     * outside the allowlist, or a forbidden Bash injection metacharacter) — no real
     * process exit code exists in that case.
     */
    private static final int SANDBOX_REJECTED_EXIT_CODE = -1;

    private final RemediationStateMachine stateMachine;
    private final SecuritySandbox securitySandbox;
    private final IncidentDiagnosticRepository incidentDiagnosticRepository;
    private final long sandboxTimeoutSeconds;

    public ExecuteRemediationService(RemediationStateMachine stateMachine,
                                       SecuritySandbox securitySandbox,
                                       IncidentDiagnosticRepository incidentDiagnosticRepository,
                                       @Value("${logsentinel.sandbox.execution-timeout-seconds:30}") long sandboxTimeoutSeconds) {
        this.stateMachine = stateMachine;
        this.securitySandbox = securitySandbox;
        this.incidentDiagnosticRepository = incidentDiagnosticRepository;
        this.sandboxTimeoutSeconds = sandboxTimeoutSeconds;
    }

    @Override
    public RemediationAction execute(ExecuteRemediationCommand command) {
        String generatedScript = resolveGeneratedScript(command.incidentId());

        RemediationAction executing = stateMachine.commitExecuting(command.incidentId(), generatedScript);
        log.info("Remediation action committed as EXECUTING", Map.of(
                "remediationActionId", String.valueOf(executing.getId()),
                "incidentId", String.valueOf(command.incidentId())
        ));

        try {
            SandboxExecutionResult result = securitySandbox.executeInIsolation(
                    generatedScript, sandboxTimeoutSeconds, TimeUnit.SECONDS);
            return stateMachine.commitClosure(executing, result.exitCode(), result.stdout(), result.stderr(),
                    OffsetDateTime.now());
        } catch (RuntimeException sandboxRejection) {
            log.error("Sandbox refused to execute remediation script", Map.of(
                    "remediationActionId", String.valueOf(executing.getId()),
                    "incidentId", String.valueOf(command.incidentId()),
                    "cause", String.valueOf(sandboxRejection.getMessage())
            ));
            return stateMachine.commitClosure(executing, SANDBOX_REJECTED_EXIT_CODE,
                    null, "Sandbox refused execution: " + sandboxRejection.getMessage(), OffsetDateTime.now());
        }
    }

    /**
     * Resolves the script to execute from the incident's persisted diagnostic
     * (LOG-US3-DB-02B, Option B). Fails fast — before Transaction A ever runs —
     * when no diagnostic exists for the incident, or its {@code suggestedScript} is
     * {@code null} (the AI's diagnostic text had no parseable fenced code block).
     */
    private String resolveGeneratedScript(UUID incidentId) {
        IncidentDiagnostic diagnostic = incidentDiagnosticRepository.findByIncidentId(incidentId)
                .orElseThrow(() -> new RemediationScriptUnavailableException(incidentId));
        String suggestedScript = diagnostic.getSuggestedScript();
        if (suggestedScript == null) {
            throw new RemediationScriptUnavailableException(incidentId);
        }
        return suggestedScript;
    }
}
