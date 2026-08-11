package com.logsentinel.application.ports.out;

import com.logsentinel.domain.model.Incident;

import java.util.Optional;
import java.util.UUID;

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

    /**
     * Finds an incident by id (LOG-US3-BE-01 — needed to look up the incident being
     * diagnosed before streaming).
     *
     * @param id the incident id
     * @return the incident, or empty if no incident with that id exists
     */
    Optional<Incident> findById(UUID id);
}
