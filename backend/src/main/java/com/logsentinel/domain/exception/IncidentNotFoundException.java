package com.logsentinel.domain.exception;

import java.util.UUID;

/**
 * Thrown when a lookup for a specific incident by id finds no matching record.
 * Pure domain exception — no framework dependency (LOG-US3-BE-01).
 */
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(UUID incidentId) {
        super("Incident not found: " + incidentId);
    }
}
