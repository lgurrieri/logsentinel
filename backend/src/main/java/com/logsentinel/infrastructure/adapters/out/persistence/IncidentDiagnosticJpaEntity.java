package com.logsentinel.infrastructure.adapters.out.persistence;

import com.logsentinel.domain.model.IncidentDiagnostic;
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
 * JPA entity mapping to the 'incident_diagnostics' table (LOG-US3-DB-02).
 * NOT exposed outside the persistence adapter - domain model is used instead.
 * <p>
 * {@code incident_id} is enforced UNIQUE + FOREIGN KEY at the database level
 * (see {@code V5__create_incident_diagnostics_table.sql}) for the one-to-one
 * relationship with {@code incidents} required by the ticket. No ORM-level
 * {@code @OneToOne}/{@code @ManyToOne} association is used here, consistent with the
 * rest of the persistence layer ({@link IncidentJpaEntity}, {@link RunbookChunkJpaEntity}),
 * which stores plain foreign-key columns rather than JPA relationship mappings.
 */
@Entity
@Table(name = "incident_diagnostics")
public class IncidentDiagnosticJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_id", nullable = false, unique = true)
    private UUID incidentId;

    @Column(name = "diagnostic_text", nullable = false, columnDefinition = "TEXT")
    private String diagnosticText;

    /**
     * The remediation script derived authoritatively by the backend from
     * {@code diagnosticText} (LOG-US3-DB-02B, design decision Option B). Nullable:
     * {@code null} whenever the AI's diagnostic did not contain a parseable fenced
     * code block (see {@code com.logsentinel.domain.service.SuggestedScriptExtractor}).
     */
    @Column(name = "suggested_script", columnDefinition = "TEXT")
    private String suggestedScript;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IncidentDiagnosticJpaEntity() {
        // Required by JPA
    }

    public IncidentDiagnosticJpaEntity(UUID incidentId, String diagnosticText, String suggestedScript) {
        this.incidentId = incidentId;
        this.diagnosticText = diagnosticText;
        this.suggestedScript = suggestedScript;
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

    /**
     * Maps this JPA entity to the pure domain model exposed through
     * {@link com.logsentinel.application.ports.out.IncidentDiagnosticRepository}
     * (LOG-US3-DB-02).
     */
    public IncidentDiagnostic toDomain() {
        return new IncidentDiagnostic(id, incidentId, diagnosticText, suggestedScript, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IncidentDiagnosticJpaEntity e)) return false;
        return id != null && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
