package com.logsentinel.application.service;

import com.logsentinel.application.ports.out.IncidentRepository;
import com.logsentinel.application.ports.out.RemediationActionRepository;
import com.logsentinel.domain.exception.IncidentNotFoundException;
import com.logsentinel.domain.model.IncidentStatus;
import com.logsentinel.domain.model.RemediationAction;
import com.logsentinel.domain.model.RemediationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Implements the transactional state machine required by LOG-US4-BE-02: two
 * independent, sequential transactions with {@code Propagation.REQUIRES_NEW},
 * so the audit trail of a remediation script execution is immune to a
 * catastrophic failure of the main thread while the script itself runs.
 * <p>
 * {@link #commitExecuting} is Transaction A — it commits immediately, before the
 * caller ever invokes the {@code SecuritySandbox} (LOG-US4-BE-01). Because it is a
 * separate Spring-managed bean method (not a self-invocation), calling it commits
 * and releases the connection regardless of what happens afterwards in the
 * caller's flow.
 * <p>
 * {@link #commitClosure} is Transaction B — it commits once the isolated sandbox
 * execution has concluded, deriving {@code SUCCESS}/{@code FAILED} from the process
 * exit code, and additionally resolving the parent incident when the script
 * succeeded. Since LOG-US4-BE-02B, it persists {@code stdout}/{@code stderr} as two
 * independent buffers instead of a single combined execution log. The sandbox
 * execution phase in between MUST NOT be wrapped in either transaction (potentially
 * slow external I/O must never hold a DB connection from the pool) — that phase is
 * orchestrated by {@code ExecuteRemediationService}.
 */
@Service
public class RemediationStateMachine {

    private final RemediationActionRepository remediationActionRepository;
    private final IncidentRepository incidentRepository;

    public RemediationStateMachine(RemediationActionRepository remediationActionRepository,
                                     IncidentRepository incidentRepository) {
        this.remediationActionRepository = remediationActionRepository;
        this.incidentRepository = incidentRepository;
    }

    /**
     * Transaction A: verifies the incident exists, then immediately persists and
     * commits a new {@link RemediationAction} in {@link RemediationStatus#EXECUTING}.
     *
     * @throws IncidentNotFoundException if no incident with {@code incidentId} exists
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RemediationAction commitExecuting(UUID incidentId, String generatedScript) {
        incidentRepository.findById(incidentId)
                .orElseThrow(() -> new IncidentNotFoundException(incidentId));
        return remediationActionRepository.save(RemediationAction.startExecuting(incidentId, generatedScript));
    }

    /**
     * Transaction B: persists the closure of an already-{@code EXECUTING} remediation
     * action, deriving {@link RemediationStatus#SUCCESS}/{@link RemediationStatus#FAILED}
     * from {@code exitCode} (zero = success), and resolves the parent incident only
     * when the script succeeded.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RemediationAction commitClosure(RemediationAction executingAction, int exitCode,
                                             String stdoutLog, String stderrLog, OffsetDateTime executedAt) {
        RemediationStatus finalStatus = exitCode == 0 ? RemediationStatus.SUCCESS : RemediationStatus.FAILED;
        RemediationAction closed = executingAction.closeWith(finalStatus, stdoutLog, stderrLog, executedAt);
        RemediationAction updated = remediationActionRepository.update(closed);

        if (finalStatus == RemediationStatus.SUCCESS) {
            incidentRepository.updateStatus(executingAction.getIncidentId(), IncidentStatus.RESOLVED);
        }

        return updated;
    }
}
