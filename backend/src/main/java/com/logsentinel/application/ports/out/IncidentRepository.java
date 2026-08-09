package com.logsentinel.application.ports.out;

import com.logsentinel.domain.model.Incident;

/**
 * Driven port (SPI) for persisting incidents.
 * Pure Java interface - implemented by the persistence adapter in infrastructure.
 */
public interface IncidentRepository {

    /**
     * Persists a new incident and returns it with generated id and timestamps.
     *
     * @param incident the incident domain object to persist
     * @return the persisted incident with id and createdAt populated
     */
    Incident save(Incident incident);
}
