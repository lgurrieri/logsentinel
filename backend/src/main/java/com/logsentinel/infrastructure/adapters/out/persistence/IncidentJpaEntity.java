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
 * JPA entity mapping to the 'incidents' table.
 * NOT exposed outside the persistence adapter - domain model is used instead.
 */
@Entity
@Table(name = "incidents")
public class IncidentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @Column(name = "urgency", nullable = false, length = 20)
    private String urgency;

    @Column(name = "raw_logs", nullable = false, columnDefinition = "TEXT")
    private String rawLogs;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IncidentJpaEntity() {
        // Required by JPA
    }

    public IncidentJpaEntity(String systemName, String urgency, String rawLogs, String status) {
        this.systemName = systemName;
        this.urgency = urgency;
        this.rawLogs = rawLogs;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getSystemName() {
        return systemName;
    }

    public String getUrgency() {
        return urgency;
    }

    public String getRawLogs() {
        return rawLogs;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IncidentJpaEntity e)) return false;
        return id != null && id.equals(e.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
