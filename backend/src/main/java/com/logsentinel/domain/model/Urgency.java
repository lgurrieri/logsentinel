package com.logsentinel.domain.model;

/**
 * Urgency level of an incident, mapped to the CHECK constraint in the database.
 */
public enum Urgency {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
