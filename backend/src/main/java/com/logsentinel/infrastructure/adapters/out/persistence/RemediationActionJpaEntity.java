package com.logsentinel.infrastructure.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the 'remediation_actions' table (LOG-US4-BE-02).
 * NOT exposed outside the persistence adapter - domain model is used instead.
 */
@Entity
@Table(name = "remediation_actions")
public class RemediationActionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "generated_script", nullable = false, columnDefinition = "TEXT")
    private String generatedScript;

    @Column(name = "execution_status", nullable = false, length = 20)
    private String executionStatus;

    @Column(name = "execution_log", columnDefinition = "TEXT")
    private String executionLog;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RemediationActionJpaEntity() {
        // Required by JPA
    }

    public RemediationActionJpaEntity(UUID id, UUID incidentId, String generatedScript, String executionStatus,
                                        String executionLog, OffsetDateTime executedAt, OffsetDateTime createdAt) {
        this.id = id;
        this.incidentId = incidentId;
        this.generatedScript = generatedScript;
        this.executionStatus = executionStatus;
        this.executionLog = executionLog;
        this.executedAt = executedAt;
        // NOTE: on INSERT, @CreationTimestamp always overwrites this with NOW() —
        // this explicit value only matters (and is preserved as-is) on UPDATE/merge,
        // so the closure phase (Transaction B) never nulls out the original createdAt.
        this.createdAt = createdAt;
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

    public String getExecutionStatus() {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemediationActionJpaEntity e)) return false;
        return id != null && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
