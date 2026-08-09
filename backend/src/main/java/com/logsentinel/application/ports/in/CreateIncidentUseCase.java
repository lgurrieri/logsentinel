package com.logsentinel.application.ports.in;

import com.logsentinel.domain.model.Incident;
import com.logsentinel.domain.model.Urgency;

/**
 * Driving port (use case) for creating a new incident.
 * Pure Java interface - no framework dependencies.
 */
public interface CreateIncidentUseCase {

    /**
     * Command record encapsulating the data needed to create an incident.
     */
    record CreateIncidentCommand(String systemName, Urgency urgency, String rawLogs) {
    }

    /**
     * Executes the incident creation use case.
     *
     * @param command the data needed to create the incident
     * @return the persisted incident with generated id and timestamps
     */
    Incident execute(CreateIncidentCommand command);
}
