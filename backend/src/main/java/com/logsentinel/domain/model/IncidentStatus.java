package com.logsentinel.domain.model;

/**
 * Lifecycle status of an incident, mapped to the CHECK constraint in the database.
 */
public enum IncidentStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
