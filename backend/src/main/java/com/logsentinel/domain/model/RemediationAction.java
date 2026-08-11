package com.logsentinel.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain entity representing a single audited execution of an AI-suggested
 * remediation script against an incident (LOG-US4-BE-02). This is a pure domain
 * object with NO framework dependencies (no JPA, no Spring).
 * <p>
 * Its lifecycle is driven by two independent, sequential transactions
 * ({@code Propagation.REQUIRES_NEW}, see {@code RemediationStateMachine}):
 * Transaction A persists a new instance via {@link #startExecuting} in
 * {@link RemediationStatus#EXECUTING} and commits immediately, before the sandboxed
 * script even starts running. Transaction B later persists the closure produced by
 * {@link #closeWith}, once the isolated execution concludes.
 */
public class RemediationAction {

    private final UUID id;
    private final UUID incidentId;
    private final String generatedScript;
    private final RemediationStatus executionStatus;
    private final String executionLog;
    private final OffsetDateTime executedAt;
    private final OffsetDateTime createdAt;

    public RemediationAction(UUID id, UUID incidentId, String generatedScript, RemediationStatus executionStatus,
                              String executionLog, OffsetDateTime executedAt, OffsetDateTime createdAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.generatedScript = generatedScript;
        this.executionStatus = executionStatus;
        this.executionLog = executionLog;
        this.executedAt = executedAt;
        this.createdAt = createdAt;
    }

    /**
     * Factory method for a remediation action about to enter Transaction A: always
     * {@link RemediationStatus#EXECUTING}, with no execution log or executedAt yet.
     * ID and createdAt are deferred to the persistence layer.
     */
    public static RemediationAction startExecuting(UUID incidentId, String generatedScript) {
        return new RemediationAction(null, incidentId, generatedScript, RemediationStatus.EXECUTING,
                null, null, null);
    }

    /**
     * Produces the closed copy of this remediation action persisted by Transaction B,
     * once the isolated sandbox execution has concluded. Never mutates {@code this} —
     * domain objects in this codebase are immutable by convention.
     */
    public RemediationAction closeWith(RemediationStatus finalStatus, String executionLog, OffsetDateTime executedAt) {
        return new RemediationAction(id, incidentId, generatedScript, finalStatus, executionLog, executedAt, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getIncidentId() {
        return incidentId;
    }

    public String getGeneratedScript() {
        return generatedScript;
    }

    public RemediationStatus getExecutionStatus() {
        return executionStatus;
    }

    public String getExecutionLog() {
        return executionLog;
    }

    public OffsetDateTime getExecutedAt() {
        return executedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
