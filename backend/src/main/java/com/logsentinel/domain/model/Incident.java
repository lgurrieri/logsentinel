package com.logsentinel.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Domain entity representing a critical incident reported by an SRE team.
 * This is a pure domain object with NO framework dependencies (no JPA, no Spring).
 */
public class Incident {

    private final UUID id;
    private final String systemName;
    private final Urgency urgency;
    private final String rawLogs;
    private final IncidentStatus status;
    private final OffsetDateTime createdAt;

    public Incident(UUID id, String systemName, Urgency urgency, String rawLogs,
                    IncidentStatus status, OffsetDateTime createdAt) {
        this.id = id;
        this.systemName = systemName;
        this.urgency = urgency;
        this.rawLogs = rawLogs;
        this.status = status;
        this.createdAt = createdAt;
    }

    /**
     * Factory method to create a new incident with OPEN status.
     * ID and timestamp are deferred to the persistence layer.
     */
    public static Incident createNew(String systemName, Urgency urgency, String rawLogs) {
        return new Incident(null, systemName, urgency, rawLogs, IncidentStatus.OPEN, null);
    }

    public UUID getId() {
        return id;
    }

    public String getSystemName() {
        return systemName;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public String getRawLogs() {
        return rawLogs;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
